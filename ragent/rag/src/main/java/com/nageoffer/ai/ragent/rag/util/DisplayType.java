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

package com.nageoffer.ai.ragent.rag.util;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Display type: the short label shown for a file. Sole producer is {@link #of(String, String)}; computed once at upload and stored
 * <p>
 * Display semantics come from the extension at upload; byte semantics come from MIME detection. Neither may be derived from the other.
 * Detecting {@code .md} by bytes usually yields only {@code text/plain}. If the display label were inferred from MIME, an uploaded
 * "spec.md" would show as txt. This enum never participates in routing; parser and chunker selection look only at MIME
 */
public enum DisplayType {

    PDF("pdf"),
    WORD("doc"),
    WORD_X("docx"),
    PPT("ppt"),
    PPT_X("pptx"),
    EXCEL("xls"),
    EXCEL_X("xlsx"),
    CSV("csv"),
    MARKDOWN("markdown"),
    TEXT("txt"),
    HTML("html"),
    JSON("json"),
    XML("xml"),
    RTF("rtf"),
    PNG("png"),
    JPG("jpg"),
    SVG("svg"),

    /**
     * Fallback: anything unrecognized is other, never the raw MIME string (the stored column is {@code VARCHAR(16)})
     */
    OTHER("other");

    /**
     * Extension to display type, the authoritative mapping
     */
    private static final Map<String, DisplayType> BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", PDF),
            Map.entry("doc", WORD),
            Map.entry("docx", WORD_X),
            Map.entry("ppt", PPT),
            Map.entry("pptx", PPT_X),
            Map.entry("xls", EXCEL),
            Map.entry("xlsx", EXCEL_X),
            Map.entry("csv", CSV),
            Map.entry("md", MARKDOWN),
            Map.entry("markdown", MARKDOWN),
            Map.entry("txt", TEXT),
            Map.entry("text", TEXT),
            Map.entry("html", HTML),
            Map.entry("htm", HTML),
            Map.entry("json", JSON),
            Map.entry("xml", XML),
            Map.entry("rtf", RTF),
            Map.entry("png", PNG),
            Map.entry("jpg", JPG),
            Map.entry("jpeg", JPG),
            Map.entry("svg", SVG)
    );

    /**
     * MIME to display type, used only when there is no extension
     */
    private static final Map<String, DisplayType> BY_MIME = Map.ofEntries(
            Map.entry("application/pdf", PDF),
            Map.entry("application/x-pdf", PDF),
            Map.entry("application/msword", WORD),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", WORD_X),
            Map.entry("application/vnd.ms-powerpoint", PPT),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", PPT_X),
            Map.entry("application/vnd.ms-excel", EXCEL),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", EXCEL_X),
            Map.entry("text/csv", CSV),
            Map.entry("text/markdown", MARKDOWN),
            Map.entry("text/x-markdown", MARKDOWN),
            Map.entry("text/plain", TEXT),
            Map.entry("text/html", HTML),
            Map.entry("application/json", JSON),
            Map.entry("application/xml", XML),
            Map.entry("application/rtf", RTF),
            Map.entry("image/png", PNG),
            Map.entry("image/jpeg", JPG),
            Map.entry("image/svg+xml", SVG)
    );

    /**
     * Spreadsheet check: frontend and backend share this list instead of each keeping its own extensions
     */
    private static final Set<DisplayType> TABULAR = Set.of(EXCEL, EXCEL_X, CSV);

    private final String code;

    DisplayType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * Whether the file is a spreadsheet
     */
    public boolean isTabular() {
        return TABULAR.contains(this);
    }

    /**
     * Every extension for this display type, looked up from {@link #BY_EXTENSION} and sent to the frontend for UI checks
     * <p>
     * Each type's code is itself a member of its extension set ({@code markdown ∈ {md, markdown}},
     * {@code jpg ∈ {jpg, jpeg}}), so comparing a stored {@code fileType} or the raw extension against this list is correct either way
     */
    public Set<String> extensions() {
        Set<String> result = new TreeSet<>();
        BY_EXTENSION.forEach((extension, type) -> {
            if (type == this) {
                result.add(extension);
            }
        });
        return result;
    }

    /**
     * Sole producer: extension first, MIME only when there is no extension; unrecognized is {@link #OTHER}
     */
    public static DisplayType of(String filename, String mimeType) {
        DisplayType byExtension = BY_EXTENSION.get(extractExtension(filename));
        if (byExtension != null) {
            return byExtension;
        }
        return BY_MIME.getOrDefault(normalizeMime(mimeType), OTHER);
    }

    /**
     * Parse a stored value; unknown values are always {@link #OTHER}
     */
    public static DisplayType from(String code) {
        if (code == null || code.isBlank()) {
            return OTHER;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (DisplayType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return OTHER;
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.trim();
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0 && separator + 1 < name.length()) {
            name = name.substring(separator + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "";
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        return semicolon >= 0 ? normalized.substring(0, semicolon).trim() : normalized;
    }
}
