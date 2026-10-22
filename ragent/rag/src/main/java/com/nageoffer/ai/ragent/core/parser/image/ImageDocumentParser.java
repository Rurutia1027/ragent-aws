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

package com.nageoffer.ai.ragent.core.parser.image;

import com.nageoffer.ai.ragent.core.parser.DocumentParser;
import com.nageoffer.ai.ragent.core.parser.ParserType;
import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import com.nageoffer.ai.ragent.core.parser.model.ImageBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.vlm.VlmService;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Image document parser (PNG / JPG / SVG). At ingest, a VLM turns the image into a Chinese description plus OCR of text in the image, and emits one {@link ImageBlock}
 * <p>
 * A standalone uploaded image has no retrievable text. Embedding {@code ![](url)} directly is noise and will never
 * be recalled, so the description goes into the embedding for recall. After the original image is uploaded to the
 * asset bucket, {@link com.nageoffer.ai.ragent.core.chunk.blockaware.ImageChunker} renders it as {@code ![caption](url)}
 * and shows it with the answer. Only exact MIME types are claimed, not the {@code image/*} wildcard; an uncovered format is an explicit error
 */
@Slf4j
@Component
public class ImageDocumentParser implements DocumentParser {

    public static final String OPT_SOURCE_FILE = "sourceFile";
    public static final String OPT_DOCUMENT_ID = "documentId";

    private final VlmService vlmService;
    private final FileStorageService fileStorageService;
    private final ImageParseProperties properties;

    public ImageDocumentParser(VlmService vlmService,
                               FileStorageService fileStorageService,
                               ImageParseProperties properties) {
        this.vlmService = vlmService;
        this.fileStorageService = fileStorageService;
        this.properties = properties;
    }

    @Override
    public String getParserType() {
        return ParserType.IMAGE.getType();
    }

    @Override
    public Map<ParseProfile, Set<String>> supportedMimeTypes() {
        return Map.of(ParseProfile.FAST, Set.of(
                "image/png",
                "image/jpeg",
                "image/jpg",
                "image/svg+xml"
        ));
    }

    @Override
    public ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            throw new ServiceException("Image parse input bytes are empty");
        }
        String sourceFile = extract(options, OPT_SOURCE_FILE, "");
        String documentId = extract(options, OPT_DOCUMENT_ID, UUID.randomUUID().toString());

        // 0. SVG normalization: rasterize the vector XML to PNG. From here the bytes and MIME match the PNG path exactly
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).equals("image/svg+xml")) {
            content = rasterizeSvg(content);
            mimeType = "image/png";
        }

        // 1. VLM image-to-text: the whole output is the description. No separator is parsed, so the prompt wording can change freely
        String description = vlmService.describeImage(
                content, mimeType, properties.getDescriptionPrompt(), properties.getMaxOutputTokens());
        description = description == null ? "" : description.strip();
        // An empty description is a failure. Letting it through only produces a link-only chunk that can never be recalled
        if (description.isBlank()) {
            throw new ServiceException("VLM returned an empty description, so no searchable text could be generated: file=" + sourceFile);
        }

        // 2. Upload the original image to the asset bucket (public-read) and take the anonymously reachable public URL
        String ext = extFromMime(mimeType);
        String filename = "assets/" + documentId + "/" + UUID.randomUUID() + "." + ext;
        StoredFileDTO stored = fileStorageService.uploadAsset(content, filename, mimeType);
        String publicUrl = fileStorageService.getPublicUrl(stored.getUrl());

        // 3. Build the ImageBlock: description is both the display / answer body and the embedding text (ImageChunker drops URL noise when rendering)
        String caption = stripExt(sourceFile);
        AssetRef asset = new AssetRef(publicUrl, mimeType);
        ImageBlock block = new ImageBlock(Provenance.ofFile(sourceFile), asset, caption, caption, description);

        log.info("图片图生文完成: file={}, descChars={}, url={}", sourceFile, description.length(), publicUrl);
        return ParsedDocument.of(List.of(block), Map.of(
                "parser", getParserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "descriptionChars", description.length()
        ));
    }

    private static String extract(Map<String, Object> options, String key, String defaultValue) {
        if (options == null) {
            return defaultValue;
        }
        Object v = options.get(key);
        return (v == null || v.toString().isBlank()) ? defaultValue : v.toString();
    }

    /**
     * Rasterizes SVG to PNG bytes. VLM vision input accepts only raster formats
     * <p>
     * A white background is required. PNGTranscoder defaults to a transparent background, and a VLM decoding a PNG with
     * alpha composites the transparent area to black or empty and returns an empty description. An SVG with no intrinsic size gets a width cap so the canvas cannot grow without bound
     */
    private static byte[] rasterizeSvg(byte[] svg) {
        try {
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_MAX_WIDTH, 1600f);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transcoder.transcode(new TranscoderInput(new ByteArrayInputStream(svg)), new TranscoderOutput(out));
            return out.toByteArray();
        } catch (Exception e) {
            throw new ServiceException("SVG rasterization failed: " + e.getMessage());
        }
    }

    private static String extFromMime(String mimeType) {
        if (mimeType == null) {
            return "png";
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            default -> "png";
        };
    }

    private static String stripExt(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
