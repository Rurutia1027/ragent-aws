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
import com.nageoffer.ai.ragent.core.chunk.text.TextSplitter;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Paragraph chunker: keep the paragraph whole, and fall back to splitting at chunk size only when it exceeds the tolerance cap
 * <p>
 * Splitting is always delegated to {@link TextSplitter}, which walks back to a boundary (newline / CJK sentence
 * end / English sentence end) and normalizes text (URL line-break repair, CJK soft-wrap join). This class does not cut by index itself
 */
@Component
public class ParagraphChunker implements BlockChunker<ParagraphBlock> {

    @Override
    public Class<ParagraphBlock> blockType() {
        return ParagraphBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(ParagraphBlock block, ChunkContext ctx) {
        if (block == null) {
            return List.of();
        }
        int overlap = ctx.budget().overlapChars();
        // Measure once against the tolerance cap. If it does not split, the paragraph fits. Only when that yields several pieces, cut again at chunk size
        List<String> pieces = TextSplitter.split(block.text(), ctx.budget().toleranceChars(), overlap);
        if (pieces.size() > 1) {
            pieces = TextSplitter.split(block.text(), ctx.budget().maxChars(), overlap);
        }
        if (pieces.isEmpty()) {
            return List.of();
        }

        ChunkMetadata metadata = ChunkMetadata.builder()
                .outlinePath(ctx.outlinePath())
                .provenance(block.provenance())
                .build();

        List<ChunkDraft> result = new ArrayList<>(pieces.size());
        for (String piece : pieces) {
            result.add(ChunkDraft.of(piece, metadata));
        }
        return ChunkDraft.pieces(result);
    }
}
