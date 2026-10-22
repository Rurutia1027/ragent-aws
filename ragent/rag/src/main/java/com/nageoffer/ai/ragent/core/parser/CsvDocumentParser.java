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

import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.core.parser.model.TableBlock;
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.detect.AutoDetectReader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CSV document parser. Treats a CSV as one regular key-value table: first row is the header, the rest are data rows,
 * and it produces a single {@link TableBlock}. Downstream, it shares TableChunker with Excel for row-level splits and key-value embedding
 * <p>
 * Claims the exact MIME so it beats Tika's {@code text/*} wildcard, and a CSV is not cut up as flat text
 */
@Slf4j
@Component
public class CsvDocumentParser implements DocumentParser {

    public static final String OPT_SOURCE_FILE = "sourceFile";

    private static final char BOM = '\uFEFF';

    @Override
    public String getParserType() {
        return ParserType.CSV.getType();
    }

    @Override
    public Map<ParseProfile, Set<String>> supportedMimeTypes() {
        return Map.of(ParseProfile.FAST, Set.of(
                "text/csv",
                "application/csv",
                "text/comma-separated-values"
        ));
    }

    @Override
    public ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            return ParsedDocument.of(List.of());
        }

        String text = decode(content);
        List<List<String>> grid = parseCsv(text);
        grid.removeIf(CsvDocumentParser::isBlankRow);
        if (grid.isEmpty()) {
            return ParsedDocument.of(List.of());
        }

        List<String> headers = grid.get(0);
        int width = headers.size();
        List<List<String>> rows = new ArrayList<>(grid.size() - 1);
        for (int i = 1; i < grid.size(); i++) {
            rows.add(padRow(grid.get(i), width));
        }

        Provenance prov = Provenance.ofFile(extractSourceFile(options));
        TableBlock block = new TableBlock(prov, headers, rows);
        return ParsedDocument.of(List.of(block), Map.of(
                "parser", getParserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "rows", rows.size(),
                "columns", width
        ));
    }

    /**
     * Decodes with Tika {@link AutoDetectReader} charset detection (UTF-8 / GBK / UTF-16 and others).
     * On failure, fall back to UTF-8. A leading BOM is always stripped
     */
    private String decode(byte[] content) {
        try (Reader reader = new AutoDetectReader(new ByteArrayInputStream(content))) {
            StringBuilder sb = new StringBuilder(content.length);
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return stripBom(sb.toString());
        } catch (Exception e) {
            log.warn("CSV 字符集探测失败，回退 UTF-8", e);
            return stripBom(new String(content, StandardCharsets.UTF_8));
        }
    }

    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
    }

    /**
     * RFC4180 parse: a comma or newline inside quotes is an ordinary character, and {@code ""} is a literal quote
     */
    private static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int len = text.length();
        while (i < len) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                inQuotes = true;
                i++;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
                i++;
            } else if (c == '\r' || c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                rows.add(current);
                current = new ArrayList<>();
                // Consume the second character of a CRLF
                i += (c == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') ? 2 : 1;
            } else {
                field.append(c);
                i++;
            }
        }
        // A trailing record that does not end with a newline
        if (!field.isEmpty() || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }
        return rows;
    }

    /**
     * When a data row is shorter than the header, pad the right with empty strings to align (keep extra cells as-is)
     */
    private static List<String> padRow(List<String> row, int width) {
        if (row.size() >= width) {
            return row;
        }
        List<String> padded = new ArrayList<>(width);
        padded.addAll(row);
        while (padded.size() < width) {
            padded.add("");
        }
        return padded;
    }

    private static boolean isBlankRow(List<String> row) {
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String extractSourceFile(Map<String, Object> options) {
        if (options == null) {
            return "";
        }
        Object v = options.get(OPT_SOURCE_FILE);
        return v == null ? "" : v.toString();
    }
}
