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

package com.nageoffer.ai.ragent.core.parser;

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
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Markdown document parser. Parses the AST with commonmark-java and emits a Block for headings, paragraphs, code blocks, lists, GFM tables, and embedded HTML
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    /**
     * commonmark parser. Thread-safe and shareable
     */
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    @Override
    public String getParserType() {
        return ParserType.MARKDOWN.getType();
    }

    @Override
    public ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            return ParsedDocument.of(List.of());
        }

        String text = new String(content, StandardCharsets.UTF_8);
        Provenance prov = Provenance.ofFile(extractSourceFile(options));

        Document doc = (Document) PARSER.parse(text);
        BlockExtractingVisitor visitor = new BlockExtractingVisitor(prov);
        doc.accept(visitor);

        return ParsedDocument.of(visitor.getBlocks(), Map.of(
                "parser", getParserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "blocks", visitor.getBlocks().size()
        ));
    }

    /**
     * MIME comes from two places, and the claim list must cover both. {@code text/x-web-markdown} is what Tika detects
     * for {@code .md}. {@code text/markdown} (RFC 7763) and {@code text/x-markdown} come from an external Content-Type;
     * Tika never emits them. They look redundant, but they must stay
     * <p>
     * Claiming {@code text/plain} is also deliberate: indented paragraphs and lists in a txt file at least keep their structure here
     */
    @Override
    public Map<ParseProfile, Set<String>> supportedMimeTypes() {
        return Map.of(ParseProfile.FAST, Set.of(
                "text/x-web-markdown",
                "text/markdown",
                "text/x-markdown",
                "text/plain"
        ));
    }

    private static String extractSourceFile(Map<String, Object> options) {
        if (options == null) {
            return "";
        }
        Object v = options.get("sourceFile");
        return v == null ? "" : v.toString();
    }

    // ===================== AST Visitor =====================

    /**
     * AST visitor: commonmark node -> ragent Block. Only top-level blocks are handled; it does not recurse into nesting
     * (a code block inside a list item still belongs to ListBlock)
     */
    private static final class BlockExtractingVisitor extends AbstractVisitor {

        private final Provenance provenance;
        private final List<Block> blocks = new ArrayList<>();

        BlockExtractingVisitor(Provenance provenance) {
            this.provenance = provenance;
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
            // Do not recurse. Inline content inside a heading is already merged
        }

        @Override
        public void visit(Paragraph paragraph) {
            // Only top-level paragraphs. A paragraph inside a list item belongs to ListBlock
            if (paragraph.getParent() instanceof ListItem) {
                return;
            }
            // An image that occupies its own line is emitted as an image block, not flattened into a paragraph that is only alt text
            Image standaloneImage = asStandaloneImage(paragraph);
            if (standaloneImage != null) {
                blocks.add(toImageBlock(standaloneImage, provenance));
                return;
            }
            String text = extractInlineText(paragraph);
            if (!text.isEmpty()) {
                blocks.add(new ParagraphBlock(provenance, text));
            }
        }

        /**
         * Embedded HTML. {@code HtmlBlock} is a leaf; its content is only in the literal. Left unhandled, the base visitChildren drops it silently
         * <p>
         * Tables get their own block type. Stored as a paragraph, they would be hard-cut by character, and the cut would land in the middle of a tag
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
            // Do not recurse
        }

        @Override
        public void visit(OrderedList orderedList) {
            blocks.add(buildListBlock(orderedList, true));
            // Do not recurse
        }

        @Override
        public void visit(org.commonmark.node.CustomBlock customBlock) {
            // A GFM TableBlock is a CustomBlock subclass
            if (customBlock instanceof TableBlock tableBlock) {
                handleTable(tableBlock);
                return;
            }
            super.visit(customBlock);
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
    }

    /**
     * Concatenates every inline text node (Text / Code / Link / Emphasis, and so on).
     * A Link keeps the {@code [text](url)} form so it matches ImageChunker downstream
     */
    private static String extractInlineText(Node parent) {
        StringBuilder sb = new StringBuilder();
        Node child = parent.getFirstChild();
        while (child != null) {
            appendInline(sb, child);
            child = child.getNext();
        }
        return sb.toString();
    }

    private static void appendInline(StringBuilder sb, Node node) {
        if (node instanceof Text t) {
            sb.append(t.getLiteral());
        } else if (node instanceof Code code) {
            sb.append('`').append(code.getLiteral()).append('`');
        } else if (node instanceof Link link) {
            String inner = extractInlineText(link);
            String dest = link.getDestination();
            sb.append('[').append(inner).append("](").append(dest).append(')');
        } else if (node instanceof Image image) {
            // The URL must be kept. With only alt text, an image in markdown can neither be cited nor previewed in the knowledge base
            sb.append("![").append(extractInlineText(image)).append("](")
                    .append(image.getDestination() == null ? "" : image.getDestination()).append(')');
        } else if (node instanceof Emphasis || node instanceof StrongEmphasis) {
            // Keep inline text and drop markdown emphasis markers
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

    /**
     * Whether the paragraph contains only one image (whitespace around it is allowed)
     */
    private static Image asStandaloneImage(Paragraph paragraph) {
        Image found = null;
        Node child = paragraph.getFirstChild();
        while (child != null) {
            if (child instanceof Image image) {
                if (found != null) {
                    return null;
                }
                found = image;
            } else if (!(child instanceof SoftLineBreak || child instanceof HardLineBreak)
                    && !(child instanceof Text text && text.getLiteral().isBlank())) {
                return null;
            }
            child = child.getNext();
        }
        return found;
    }

    /**
     * Image node -> image block
     * <p>
     * The address is the author's original address (external link or relative path) and is not uploaded as an asset,
     * so there is no image-to-text description. At chunk time the embedding text falls back to the link itself
     */
    private static ImageBlock toImageBlock(Image image, Provenance provenance) {
        String url = image.getDestination() == null ? "" : image.getDestination();
        String altText = extractInlineText(image);
        return new ImageBlock(
                provenance,
                new AssetRef(url, guessImageMime(url)),
                image.getTitle(),
                altText
        );
    }

    /**
     * Guesses MIME from the address suffix. An image address is not detected from bytes, so the extension is the only reasonable hint
     */
    private static String guessImageMime(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return null;
    }

    private static String stripTrailingNewline(String s) {
        if (s == null) {
            return "";
        }
        if (s.endsWith("\n")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }
}
