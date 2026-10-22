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
import com.nageoffer.ai.ragent.core.parser.model.HtmlTableBlock;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML table chunker: split on tr boundaries. Each chunk repeats the header row and is wrapped in a complete table
 * <p>
 * Do not convert to a pipe table. Merged cells and newlines inside cells are distorted when expanded into a 2-D
 * table, so display and retrieval share the same HTML
 */
@Component
public class HtmlTableChunker implements BlockChunker<HtmlTableBlock> {

    private static final Pattern ROW = Pattern.compile("<tr\\b[^>]*>.*?</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * A colspan / rowspan of 1 expresses no merge, but MinerU writes it on every cell. A table of about ten rows can triple in size
     */
    private static final Pattern NO_OP_SPAN = Pattern.compile("\\s+(?:colspan|rowspan)\\s*=\\s*[\"']?1[\"']?",
            Pattern.CASE_INSENSITIVE);

    private static final String TABLE_CLOSE = "</table>";

    @Override
    public Class<HtmlTableBlock> blockType() {
        return HtmlTableBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(HtmlTableBlock block, ChunkContext ctx) {
        if (block == null || !StringUtils.hasText(block.html())) {
            return List.of();
        }
        ChunkMetadata metadata = ChunkMetadata.builder()
                .outlinePath(ctx.outlinePath())
                .provenance(block.provenance())
                .build();

        String html = NO_OP_SPAN.matcher(block.html()).replaceAll("");
        List<String> rows = splitRows(html);
        // Header only, or no rows found at all: there is no boundary to cut on, so store the block as-is. Better to exceed the budget than to cut inside a tag
        if (rows.size() < 2) {
            return List.of(ChunkDraft.of(html, metadata));
        }

        String open = openTag(html);
        String header = rows.get(0);
        int maxRows = Math.max(1, ctx.budget().rowsPerChunk());
        // Leave the whole table intact when it fits under the tolerance cap. After a cut, each chunk repeats the header, but rows cannot be compared across chunks
        int budget = rows.size() - 1 <= maxRows && html.length() <= ctx.budget().toleranceChars()
                ? ctx.budget().toleranceChars()
                : Math.max(1, ctx.budget().maxChars());
        // The wrapper and the header are repeated on every chunk. Subtract them from the budget first, or the rendered chunk will always exceed it
        int overhead = open.length() + TABLE_CLOSE.length() + header.length();

        List<ChunkDraft> result = new ArrayList<>();
        List<String> group = new ArrayList<>();
        int groupLen = 0;
        for (String row : rows.subList(1, rows.size())) {
            boolean overCap = group.size() >= maxRows;
            boolean overBudget = !group.isEmpty() && overhead + groupLen + row.length() > budget;
            if (overCap || overBudget) {
                result.add(ChunkDraft.of(render(open, header, group), metadata));
                group = new ArrayList<>();
                groupLen = 0;
            }
            group.add(row);
            groupLen += row.length();
        }
        result.add(ChunkDraft.of(render(open, header, group), metadata));
        return ChunkDraft.pieces(result);
    }

    /**
     * Cuts out each tr fragment, including the tag itself. The first one is the header row
     */
    private static List<String> splitRows(String html) {
        List<String> rows = new ArrayList<>();
        Matcher matcher = ROW.matcher(html);
        while (matcher.find()) {
            rows.add(matcher.group());
        }
        return rows;
    }

    /**
     * Use the original table open tag rather than a hardcoded one, so author attributes such as border / class travel with every chunk
     */
    private static String openTag(String html) {
        int end = html.indexOf('>');
        return end < 0 ? "<table>" : html.substring(0, end + 1);
    }

    private static String render(String open, String header, List<String> rows) {
        StringBuilder sb = new StringBuilder(open).append(header);
        for (String row : rows) {
            sb.append(row);
        }
        return sb.append(TABLE_CLOSE).toString();
    }
}
