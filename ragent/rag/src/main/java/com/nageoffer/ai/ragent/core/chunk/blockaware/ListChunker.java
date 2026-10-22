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
import com.nageoffer.ai.ragent.core.parser.model.ListBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * List chunker: greedily group list items by rendered character size, and never cut through an item
 * <p>
 * Measure characters, not item count. Count is a bad proxy for size: a dozen short entries can be about two
 * hundred characters, and cutting by count only for the packer to merge them back leaves a run of extra blank lines in the body
 */
@Component
public class ListChunker implements BlockChunker<ListBlock> {

    @Override
    public Class<ListBlock> blockType() {
        return ListBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(ListBlock block, ChunkContext ctx) {
        if (block == null || block.items() == null || block.items().isEmpty()) {
            return List.of();
        }
        List<String> items = block.items();
        ChunkMetadata metadata = ChunkMetadata.builder()
                .outlinePath(ctx.outlinePath())
                .provenance(block.provenance())
                .build();

        // Leave the whole list intact when it fits under the tolerance cap. After a cut, a question like "which materials must be submitted" can recall only half
        int budget = Math.max(1, renderedLength(block) <= ctx.budget().toleranceChars()
                ? ctx.budget().toleranceChars()
                : ctx.budget().maxChars());
        List<ChunkDraft> result = new ArrayList<>();
        int start = 0;
        int cost = 0;
        for (int i = 0; i < items.size(); i++) {
            // Add one for the newline between items. An item that itself exceeds the budget becomes its own chunk; a hard cut would slice the entry in half
            int itemCost = renderItem(block, i + 1, items.get(i)).length() + 1;
            if (i > start && cost + itemCost > budget) {
                result.add(buildDraft(items.subList(start, i), start + 1, block, metadata));
                start = i;
                cost = 0;
            }
            cost += itemCost;
        }
        result.add(buildDraft(items.subList(start, items.size()), start + 1, block, metadata));
        return ChunkDraft.pieces(result);
    }

    /**
     * {@code startNumber} applies only to ordered lists, as this chunk's starting number
     */
    private ChunkDraft buildDraft(List<String> items, int startNumber, ListBlock block,
                                 ChunkMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(renderItem(block, startNumber + i, items.get(i)));
        }
        return ChunkDraft.of(sb.toString(), metadata);
    }

    /**
     * Rendered size of the whole list, including newlines between items, used to decide whether to cut
     */
    private static int renderedLength(ListBlock block) {
        int total = 0;
        List<String> items = block.items();
        for (int i = 0; i < items.size(); i++) {
            total += renderItem(block, i + 1, items.get(i)).length() + 1;
        }
        return total;
    }

    /**
     * Renders one item, and that rendering is also the size used when splitting to the budget
     */
    private static String renderItem(ListBlock block, int number, String item) {
        return block.ordered() ? number + ". " + item : "- " + item;
    }
}
