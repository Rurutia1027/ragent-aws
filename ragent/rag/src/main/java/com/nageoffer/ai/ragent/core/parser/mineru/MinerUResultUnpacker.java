/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.core.parser.mineru;

import com.nageoffer.ai.ragent.core.parser.image.ImageParseProperties;
import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.CodeBlock;
import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import com.nageoffer.ai.ragent.core.parser.model.HtmlTableBlock;
import com.nageoffer.ai.ragent.core.parser.model.ImageBlock;
import com.nageoffer.ai.ragent.core.parser.model.ListBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.vlm.VlmService;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU result unpacker: zip bytes -> ParsedDocument
 * <p>
 * The zip holds one markdown file plus images. Each image is uploaded to the asset bucket in exchange for a public
 * URL, then image addresses in the markdown AST are rewritten by the {path inside the zip -> URL} map. An image
 * at the start of a paragraph is promoted to {@link ImageBlock}. The bytes are in hand, so a VLM description is
 * generated too; otherwise an embedded image's embedding text is only a URL
 */
@Slf4j
@Component
public class MinerUResultUnpacker {

    private static final Parser MARKDOWN_PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    private final FileStorageService fileStorageService;
    private final VlmService vlmService;
    private final ImageParseProperties imageParseProperties;

    public MinerUResultUnpacker(FileStorageService fileStorageService,
                                VlmService vlmService,
                                ImageParseProperties imageParseProperties) {
        this.fileStorageService = fileStorageService;
        this.vlmService = vlmService;
        this.imageParseProperties = imageParseProperties;
    }

    /**
     * Unpacks a zip into a ParsedDocument. Each ImageBlock already carries an AssetRef with a public URL
     *
     * @param sourceFile source id written to Provenance.sourceFile
     * @param documentId used to name asset keys, stored as {@code assets/{documentId}/{uuid}.{ext}}
     */
    public ParsedDocument unpack(byte[] zipBytes, String sourceFile, String documentId) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new ServiceException("MinerU zip bytes are empty");
        }

        ZipContents contents = readZip(zipBytes);
        if (contents.markdown == null) {
            throw new ServiceException("No markdown file found in the MinerU zip");
        }

        // Upload every image and get a {path inside the zip -> public URL} map
        Map<String, String> imageUrlMap = uploadImages(contents.images, documentId);
        Map<String, String> imageDescriptionMap = describeImages(contents.images);

        // Parse the markdown into a Block list
        Provenance prov = Provenance.ofFile(sourceFile);
        Document doc = (Document) MARKDOWN_PARSER.parse(contents.markdown);
        UnpackVisitor visitor = new UnpackVisitor(prov, imageUrlMap, imageDescriptionMap);
        doc.accept(visitor);

        return ParsedDocument.of(visitor.getBlocks(), Map.of(
                "parser", "MinerU",
                "imagesUploaded", imageUrlMap.size(),
                "imagesDescribed", imageDescriptionMap.size(),
                "blocks", visitor.getBlocks().size()
        ));
    }

    /**
     * Snapshot of one file's zip contents
     */
    private record ZipContents(String markdown, Map<String, byte[]> images) {
    }

    private ZipContents readZip(byte[] zipBytes) {
        String markdown = null;
        Map<String, byte[]> images = new HashMap<>();

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                byte[] data = readAll(zin);

                if (name.toLowerCase(Locale.ROOT).endsWith(".md") && markdown == null) {
                    markdown = new String(data, StandardCharsets.UTF_8);
                } else if (isImage(name)) {
                    images.put(name, data);
                }
            }
        } catch (IOException e) {
            throw new ServiceException("MinerU zip extraction failed: " + e.getMessage());
        }
        return new ZipContents(markdown, images);
    }

    private static byte[] readAll(ZipInputStream zin) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zin.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    /**
     * Uploads every image to the asset bucket and returns {path inside the zip -> public URL}
     */
    private Map<String, String> uploadImages(Map<String, byte[]> images, String documentId) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, byte[]> e : images.entrySet()) {
            String zipPath = e.getKey();
            byte[] data = e.getValue();
            String ext = extractExt(zipPath);
            String filename = "assets/" + documentId + "/" + UUID.randomUUID() + "." + ext;
            String mime = inferMime(ext);
            try {
                StoredFileDTO stored = fileStorageService.uploadAsset(data, filename, mime);
                // Turn it into a public URL the browser can open directly (the asset bucket is public-read) and bake it into the markdown image link before storage
                String publicUrl = fileStorageService.getPublicUrl(stored.getUrl());
                result.put(zipPath, publicUrl);
            } catch (Exception ex) {
                log.error("MinerU 图片上传失败 zipPath={}", zipPath, ex);
                throw new ServiceException("MinerU image upload failed " + zipPath + ": " + ex.getMessage());
            }
        }
        return result;
    }

    /**
     * Image-to-text, one image at a time. The key is the path inside the zip, the same key as {@code imageUrlMap}
     * <p>
     * One failure is logged and does not abort. Failing to recall one figure is far better than failing the whole
     * document. Calls are serial, so a document with many images parses noticeably slower, but MinerU's cloud parse is already measured in minutes
     */
    private Map<String, String> describeImages(Map<String, byte[]> images) {
        if (!imageParseProperties.isEmbeddedDescribeEnabled() || images.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, byte[]> e : images.entrySet()) {
            String zipPath = e.getKey();
            try {
                String description = vlmService.describeImage(e.getValue(), inferMime(extractExt(zipPath)),
                        imageParseProperties.getDescriptionPrompt(), imageParseProperties.getMaxOutputTokens());
                if (description != null && !description.isBlank()) {
                    result.put(zipPath, description.strip());
                }
            } catch (Exception ex) {
                log.warn("MinerU 内嵌图图生文失败，该图向量文本将只剩链接 zipPath={}", zipPath, ex);
            }
        }
        return result;
    }

    private static String extractExt(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1).toLowerCase(Locale.ROOT) : "bin";
    }

    private static String inferMime(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    // ===================== AST Visitor =====================

    /**
     * Walks the markdown AST and emits Blocks
     * <p>
     * An Image at the start of a paragraph is peeled off and promoted to {@link ImageBlock}; the rest starts a new
     * ParagraphBlock. An inline Image stays in the ParagraphBlock text. Both links have already been replaced with asset-bucket URLs
     */
    private static final class UnpackVisitor extends AbstractVisitor {

        private final Provenance provenance;
        private final Map<String, String> imageUrlMap;
        private final Map<String, String> imageDescriptionMap;
        private final List<Block> blocks = new ArrayList<>();

        UnpackVisitor(Provenance provenance, Map<String, String> imageUrlMap,
                      Map<String, String> imageDescriptionMap) {
            this.provenance = provenance;
            this.imageUrlMap = imageUrlMap;
            this.imageDescriptionMap = imageDescriptionMap;
        }

        List<Block> getBlocks() {
            return blocks;
        }

        @Override
        public void visit(Heading heading) {
            blocks.add(new HeadingBlock(
                    provenance,
                    heading.getLevel(),
                    extractInlineText(heading)
            ));
        }

        @Override
        public void visit(Paragraph paragraph) {
            if (paragraph.getParent() instanceof ListItem) {
                return;
            }

            Node rest = paragraph.getFirstChild();
            while (rest != null) {
                if (rest instanceof Image img) {
                    handleStandaloneImage(img);
                } else if (!isBlank(rest)) {
                    break;
                }
                rest = rest.getNext();
            }

            String text = extractInlineTextFrom(rest);
            if (!text.isEmpty()) {
                blocks.add(new ParagraphBlock(provenance, text));
            }
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            blocks.add(new CodeBlock(
                    provenance,
                    codeBlock.getInfo(),
                    stripTrailingNewline(codeBlock.getLiteral())
            ));
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            blocks.add(new CodeBlock(
                    provenance,
                    null,
                    stripTrailingNewline(codeBlock.getLiteral())
            ));
        }

        @Override
        public void visit(BulletList bulletList) {
            blocks.add(buildListBlock(bulletList, false));
        }

        @Override
        public void visit(OrderedList orderedList) {
            blocks.add(buildListBlock(orderedList, true));
        }

        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof TableBlock tableBlock) {
                handleTable(tableBlock);
                return;
            }
            super.visit(customBlock);
        }

        /**
         * MinerU embeds tables as raw HTML inside the markdown, and commonmark classifies that as HtmlBlock
         * <p>
         * Tables are lifted out: stored as a paragraph they would be hard-cut by character, with the cut in the middle of a tag. Other HTML still goes through a paragraph so nothing is dropped
         */
        @Override
        public void visit(HtmlBlock htmlBlock) {
            String html = htmlBlock.getLiteral() == null ? "" : htmlBlock.getLiteral().strip();
            if (html.isEmpty()) {
                return;
            }
            blocks.add(html.regionMatches(true, 0, "<table", 0, 6)
                    ? new HtmlTableBlock(provenance, html)
                    : new ParagraphBlock(provenance, html));
        }

        private static boolean isBlank(Node node) {
            return node instanceof SoftLineBreak
                    || node instanceof HardLineBreak
                    || (node instanceof Text t && t.getLiteral().trim().isEmpty());
        }

        private void handleStandaloneImage(Image image) {
            String rawDest = image.getDestination();
            String zipPath = resolveZipPath(rawDest);
            String resolved = resolveImageUrl(rawDest);
            String caption = extractInlineText(image);

            AssetRef asset = new AssetRef(resolved, inferMimeFromUrl(resolved));
            blocks.add(new ImageBlock(
                    provenance,
                    asset, caption, caption,
                    zipPath == null ? null : imageDescriptionMap.get(zipPath)
            ));
        }

        private String resolveImageUrl(String rawDest) {
            String zipPath = resolveZipPath(rawDest);
            if (zipPath != null) {
                return imageUrlMap.get(zipPath);
            }
            return rawDest == null ? "" : rawDest;
        }

        /**
         * Restores an image address in the markdown to the path inside the zip. The URL map and the description map share this key
         */
        private String resolveZipPath(String rawDest) {
            if (rawDest == null) {
                return null;
            }
            // Prefer an exact match
            if (imageUrlMap.containsKey(rawDest)) {
                return rawDest;
            }
            // Fuzzy match: MinerU markdown may write ./images/xxx or images/xxx
            String norm = rawDest.replaceFirst("^\\./", "");
            if (imageUrlMap.containsKey(norm)) {
                return norm;
            }
            // Fall back to matching by file name
            int idx = norm.lastIndexOf('/');
            String fileName = idx >= 0 ? norm.substring(idx + 1) : norm;
            for (String key : imageUrlMap.keySet()) {
                if (key.endsWith("/" + fileName) || key.equals(fileName)) {
                    return key;
                }
            }
            return null;
        }

        private static String inferMimeFromUrl(String url) {
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".gif")) return "image/gif";
            return "application/octet-stream";
        }

        private ListBlock buildListBlock(Node listNode, boolean ordered) {
            List<String> items = new ArrayList<>();
            Node child = listNode.getFirstChild();
            while (child != null) {
                if (child instanceof ListItem) {
                    items.add(extractInlineText(child).trim());
                }
                child = child.getNext();
            }
            return new ListBlock(provenance, ordered, items);
        }

        private void handleTable(TableBlock tableBlock) {
            List<String> headers = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            Node child = tableBlock.getFirstChild();
            while (child != null) {
                if (child instanceof TableHead head) {
                    Node hr = head.getFirstChild();
                    if (hr instanceof TableRow tr) {
                        headers.addAll(extractCellTexts(tr));
                    }
                } else if (child instanceof TableBody body) {
                    Node tr = body.getFirstChild();
                    while (tr != null) {
                        if (tr instanceof TableRow row) {
                            rows.add(extractCellTexts(row));
                        }
                        tr = tr.getNext();
                    }
                }
                child = child.getNext();
            }

            blocks.add(new com.nageoffer.ai.ragent.core.parser.model.TableBlock(
                    provenance,
                    headers,
                    rows
            ));
        }

        private List<String> extractCellTexts(TableRow row) {
            List<String> cells = new ArrayList<>();
            Node cell = row.getFirstChild();
            while (cell != null) {
                if (cell instanceof TableCell tc) {
                    cells.add(extractInlineText(tc).trim());
                }
                cell = cell.getNext();
            }
            return cells;
        }

        private String extractInlineText(Node parent) {
            return extractInlineTextFrom(parent.getFirstChild());
        }

        /**
         * Concatenates inline text starting at a given sibling (renders what remains after an image is peeled off the start of a paragraph)
         */
        private String extractInlineTextFrom(Node start) {
            StringBuilder sb = new StringBuilder();
            Node node = start;
            while (node != null) {
                appendInline(sb, node);
                node = node.getNext();
            }
            return sb.toString();
        }

        private void appendInline(StringBuilder sb, Node node) {
            if (node instanceof Text t) {
                sb.append(t.getLiteral());
            } else if (node instanceof Code code) {
                sb.append('`').append(code.getLiteral()).append('`');
            } else if (node instanceof Link link) {
                String inner = extractInlineText(link);
                sb.append('[').append(inner).append("](").append(link.getDestination()).append(')');
            } else if (node instanceof Image img) {
                // An inline image keeps the ![alt](url) form and is stored with the paragraph text
                String alt = extractInlineText(img);
                String resolved = resolveImageUrl(img.getDestination());
                sb.append("![").append(alt).append("](").append(resolved).append(')');
            } else if (node instanceof Emphasis || node instanceof StrongEmphasis) {
                sb.append(extractInlineText(node));
            } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                sb.append('\n');
            } else if (node.getFirstChild() != null) {
                Node child = node.getFirstChild();
                while (child != null) {
                    appendInline(sb, child);
                    child = child.getNext();
                }
            }
        }

        private static String stripTrailingNewline(String s) {
            if (s == null) {
                return "";
            }
            return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
        }
    }
}
