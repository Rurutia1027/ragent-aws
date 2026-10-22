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

package com.nageoffer.ai.ragent.rag.core.source;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.GroundingChunk;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembler of grounding snippets for recommended questions
 * <p>
 * Selection only: dedup retrieved snippets (KB hits) by document, keep the highest-scoring snippet, cap the count, and persist them with the assistant message
 * <p>
 * Separate from {@link SourcesAssembler}: that one produces document-level sources for the panel and preview (100-character excerpt).
 * This one produces snippets for recommendation grounding (deduped by document, cap 8)
 * <p>
 * No character budget: the snippets are fed only to the recommendation generator. Prompt size is controlled by the consumer at the model-call boundary
 */
@Component
@RequiredArgsConstructor
public class GroundingChunksAssembler {

    /**
     * Cap on grounding snippets. Bounds storage and prompt size (documents are already deduped; 8 is enough to support 3 follow-ups)
     */
    private static final int MAX_CHUNKS = 8;

    /**
     * Assemble the grounding snippet list from the retrieval context's intent slices
     *
     * @param intentChunks intent id to hit snippets (KB)
     * @return grounding snippets; empty when nothing hit
     */
    public List<GroundingChunk> assemble(Map<String, List<RetrievedChunk>> intentChunks) {
        if (CollUtil.isEmpty(intentChunks)) {
            return List.of();
        }

        // Group by docId and keep the highest-scoring snippet (same meaning as SourcesAssembler, so documents stay diverse)
        Map<String, RetrievedChunk> bestByDoc = new LinkedHashMap<>();
        intentChunks.values().stream()
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .filter(chunk -> chunk != null
                        && StrUtil.isNotBlank(chunk.getDocId())
                        && StrUtil.isNotBlank(chunk.getText()))
                .forEach(chunk -> bestByDoc.merge(chunk.getDocId(), chunk,
                        (existing, candidate) -> score(candidate) > score(existing) ? candidate : existing));
        if (bestByDoc.isEmpty()) {
            return List.of();
        }

        // Take the cap in descending highest-score order; text is the full text (the budget is applied by the consumer at the model-call boundary)
        return bestByDoc.values().stream()
                .sorted(Comparator.comparingDouble(GroundingChunksAssembler::score).reversed())
                .limit(MAX_CHUNKS)
                .map(chunk -> GroundingChunk.builder()
                        .docName(StrUtil.blankToDefault(chunk.getDocName(), chunk.getDocId()))
                        .text(StrUtil.trim(chunk.getText()))
                        .build())
                .toList();
    }

    private static double score(RetrievedChunk chunk) {
        return chunk.getScore() == null ? 0D : chunk.getScore();
    }
}
