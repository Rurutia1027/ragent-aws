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

package com.nageoffer.ai.ragent.core.chunk.blockaware;

import com.nageoffer.ai.ragent.core.chunk.model.ChunkDraft;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkMetadata;
import com.nageoffer.ai.ragent.core.parser.model.TableBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Table chunker: accumulate key-value rendering length up to the budget. Every chunk carries the full header,
 * and a single row over budget becomes its own atomic chunk
 * <p>
 * {@code rowsPerChunk} is only a hard cap, so wide tables stay under the embedding limit and narrow tables are
 * not over-fragmented. Display text is a complete markdown table. Embedding text uses {@code column: value},
 * because a markdown table aligns names and values by position, which an embedding model cannot read. The header
 * is not copied into the embedding text: the key-value body already carries the column name in every cell, and a
 * repeated prefix pulls every chunk of the same table in one direction and compresses the distance between them.
 * Table identity is carried by the sheet name through the outline path
 */
@Component
public class TableChunker implements BlockChunker<TableBlock> {

    @Override
    public Class<TableBlock> blockType() {
        return TableBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(TableBlock block, ChunkContext ctx) {
        if (block == null) {
            return List.of();
        }
        List<String> headers = block.headers() == null ? List.of() : block.headers();
        List<List<String>> rows = block.rows() == null ? List.of() : block.rows();
        if (headers.isEmpty() && rows.isEmpty()) {
            return List.of();
        }

        // The budget measures key-value rows only, and deliberately ignores the outline prefix the assembler appends. Deducting it would drive the usable budget toward 0 in a deep section,
        // collapsing to one row per chunk, most of each chunk an identical prefix
        int maxRows = Math.max(1, ctx.budget().rowsPerChunk());
        // Leave the whole table intact when it fits under the tolerance cap. After a cut, each chunk repeats the header, but rows cannot be compared across chunks
        int budget = rows.size() <= maxRows
                && renderKeyValueRows(headers, rows).length() <= ctx.budget().toleranceChars()
                ? ctx.budget().toleranceChars()
                : Math.max(1, ctx.budget().maxChars());

        List<ChunkDraft> result = new ArrayList<>();

        if (rows.isEmpty()) {
            result.add(buildDraft(block, ctx, headers, List.of()));
            return result;
        }

        // Greedy accumulation: flush a chunk when the hard cap is exceeded, or when the chunk is non-empty and the next row would exceed the budget
        List<List<String>> group = new ArrayList<>();
        int groupCost = 0;
        for (List<String> row : rows) {
            int rowCost = renderKeyValueRow(headers, row).length();
            boolean overCap = group.size() >= maxRows;
            boolean overBudget = !group.isEmpty() && groupCost + rowCost > budget;
            if (overCap || overBudget) {
                result.add(buildDraft(block, ctx, headers, group));
                group = new ArrayList<>();
                groupCost = 0;
            }
            group.add(row);
            groupCost += rowCost;
        }
        result.add(buildDraft(block, ctx, headers, group));
        return ChunkDraft.pieces(result);
    }

    private ChunkDraft buildDraft(TableBlock block, ChunkContext ctx, List<String> headers, List<List<String>> rows) {
        ChunkMetadata metadata = ChunkMetadata.builder()
                .outlinePath(ctx.outlinePath())
                .provenance(block.provenance())
                .build();
        // The assembler prepends the outline path to embedding text. Here, supply only the key-value body so the prefix is not repeated
        return ChunkDraft.of(renderMarkdownTable(headers, rows), renderKeyValueRows(headers, rows), metadata);
    }

    private String renderKeyValueRows(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            String line = renderKeyValueRow(headers, row);
            if (line.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Renders one row as {@code column: value}, joined by "; ", skipping empty values. An all-empty row returns an empty string.
     * The same rendering is the row size used when splitting to the budget
     */
    private String renderKeyValueRow(List<String> headers, List<String> row) {
        StringBuilder line = new StringBuilder();
        for (int c = 0; c < row.size(); c++) {
            String value = row.get(c);
            if (value == null || value.isEmpty()) {
                continue;
            }
            String key = c < headers.size() ? headers.get(c) : "";
            if (!line.isEmpty()) {
                line.append("; ");
            }
            if (!key.isEmpty()) {
                line.append(oneLine(key)).append(": ");
            }
            line.append(oneLine(value));
        }
        return line.toString();
    }

    /**
     * Collapses newlines inside a cell to spaces. A line break between key and value hurts retrieval
     */
    private static String oneLine(String text) {
        return text.replaceAll("\\r\\n|\\r|\\n", " ");
    }

    private String renderMarkdownTable(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers);
        appendSeparator(sb, headers.size());
        for (List<String> row : rows) {
            appendRow(sb, row);
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private void appendRow(StringBuilder sb, List<String> cells) {
        sb.append('|');
        for (String cell : cells) {
            sb.append(' ').append(sanitizeCell(cell)).append(" |");
        }
        sb.append('\n');
    }

    /**
     * Cleans a cell so it fits markdown table syntax
     * <p>
     * A newline inside a cell (Excel Alt+Enter) becomes {@code <br>}. A raw newline cuts the table row in the middle
     * and the whole chunk degrades into an ordinary paragraph. Escape pipes: a literal {@code |} inside a cell would be read as a column separator
     */
    private String sanitizeCell(String cell) {
        if (cell == null || cell.isEmpty()) {
            return "";
        }
        return cell.replace("|", "\\|").replaceAll("\\r\\n|\\r|\\n", "<br>");
    }

    private void appendSeparator(StringBuilder sb, int colCount) {
        sb.append('|');
        sb.append("---|".repeat(Math.max(0, colCount)));
        sb.append('\n');
    }

}
