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

package com.nageoffer.ai.ragent.rag.core.retrieval.postprocessor;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Evidence relevance gate
 * <p>
 * Retrieval only promises the N most similar items. When the store has no answer it still returns a full set, and downstream only checks that evidence text is non-empty, so noise enters the prompt.
 * The gate decides from the highest rerank score of the whole batch. If it fails, the whole batch is dropped. It only decides batch keep-or-drop; once the batch passes, weak evidence is kept with it
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceGatePostProcessor implements SearchResultPostProcessor, InitializingBean {

    private final SearchChannelProperties searchChannelProperties;
    private final RAGConfigProperties ragConfigProperties;

    /**
     * The gate's only criterion comes from rerank
     */
    @Override
    public void afterPropertiesSet() {
        double minRerankScore = searchChannelProperties.getEvidence().getMinRerankScore();
        if (minRerankScore > 0 && Boolean.FALSE.equals(ragConfigProperties.getRerankEnabled())) {
            throw new IllegalStateException(String.format(
                    "rag.search.evidence.min-rerank-score(%s) needs rerank scores, but rag.rerank.enabled=false: "
                            + "闸门将无分可读、恒放行；请开启精排或把下限填 0",
                    minRerankScore));
        }
    }

    @Override
    public String getName() {
        return "EvidenceGate";
    }

    @Override
    public int getOrder() {
        return 15;  // After Rerank(10) assigns scores, before MetadataEnrichment(20) looks rows up
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return searchChannelProperties.getEvidence().getMinRerankScore() > 0;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        // If there is no score to read, always pass: a noop degradation only truncates and does not score.
        // Blocking anyway would shut off the whole KB side when rerank is least stable, and it would look the same as an empty store
        // Reaching here means the gate is spinning idle; log at warn — this should not happen when rerank is healthy
        Float topScore = maxRerankScore(chunks);
        if (topScore == null) {
            log.warn("检索归因 - 证据闸门: 本批 {} 条无精排分可读，闸门空转放行", chunks.size());
            return chunks;
        }

        double minRerankScore = searchChannelProperties.getEvidence().getMinRerankScore();
        if (topScore >= minRerankScore) {
            return chunks;
        }

        log.info("检索归因 - 证据闸门: 最高精排分 {} 低于下限 {}，丢弃全部 {} 条证据",
                topScore, minRerankScore, chunks.size());
        return List.of();
    }

    /**
     * Returns null when the whole batch has no scores.
     * Judge by the highest score, not item by item: a false drop costs more than a false pass.
     * Do not take the first item: {@code RerankClient} does not promise return order, and backfilled items have no score
     */
    private Float maxRerankScore(List<RetrievedChunk> chunks) {
        Float max = null;
        for (RetrievedChunk chunk : chunks) {
            Float score = chunk.getRerankScore();
            if (score == null || !Float.isFinite(score)) {
                continue;
            }
            if (max == null || score > max) {
                max = score;
            }
        }
        return max;
    }
}
