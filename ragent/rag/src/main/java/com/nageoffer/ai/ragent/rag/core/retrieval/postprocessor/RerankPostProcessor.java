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
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelType;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rerank post-processor
 * <p>
 * Re-ranks results with a rerank model.
 * This is the last processor and emits the final top-K
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankPostProcessor implements SearchResultPostProcessor {

    private final RerankService rerankService;
    private final RAGConfigProperties ragConfigProperties;

    @Override
    public String getName() {
        return "Rerank";
    }

    @Override
    public int getOrder() {
        return 10;  // Runs last
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return ragConfigProperties.getRerankEnabled();
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            log.info("Chunk 列表为空，跳过 Rerank");
            return chunks;
        }

        List<RetrievedChunk> reranked = rerankService.rerank(
                context.getMainQuestion(),
                chunks,
                context.getBudget().contextTopK()
        );

        logScoreSpread(reranked);
        logAttribution(chunks, reranked, results);
        return reranked;
    }

    /**
     * Logs the high and low ends of this batch's rerank scores, for calibrating {@code rag.search.evidence.min-rerank-score}.
     * Not folded into the multi-channel attribution below: that block returns early for a single channel, which is exactly when the gate being off most needs this line
     */
    private void logScoreSpread(List<RetrievedChunk> reranked) {
        List<Float> scores = reranked.stream()
                .map(RetrievedChunk::getRerankScore)
                .filter(score -> score != null && Float.isFinite(score))
                .toList();
        if (scores.isEmpty()) {
            return;
        }
        log.info("检索归因 - 精排分布: {} 条有分, 最高 {}, 最低 {}",
                scores.size(),
                scores.stream().max(Float::compare).orElseThrow(),
                scores.stream().min(Float::compare).orElseThrow());
    }

    /**
     * Attribution log: compare per-channel candidate counts before and after rerank, especially "graph evidence survival"
     * <p>
     * If a lot of graph evidence enters rerank and almost none survives, it is pure cost right now (fills candidates, takes slots, then is dropped).
     * Lower the graph weight ({@code fusion.channel-weights.graph}) or first make its long evidence rankable, then decide whether to keep it
     */
    private void logAttribution(List<RetrievedChunk> before,
                                List<RetrievedChunk> after,
                                List<SearchChannelResult> results) {
        if (results == null || results.size() <= 1) {
            return;
        }
        Map<String, Set<SearchChannelType>> index = ChannelAttribution.index(results);
        log.info("检索归因 - Rerank 输入按通道: {}, 输出 top{} 按通道: {}",
                ChannelAttribution.format(ChannelAttribution.countByChannel(before, index)),
                after.size(),
                ChannelAttribution.format(ChannelAttribution.countByChannel(after, index)));

        // Decide by whether the graph channel is present, not by graphIn > 0: 0/0 is the shape that most needs to be seen — the graph recalled, then lost everything at fusion truncation.
        // Gating the log on input count would silence it exactly when the incident happens
        boolean graphChannelPresent = results.stream()
                .anyMatch(result -> result.getChannelType() == SearchChannelType.GRAPH);
        if (graphChannelPresent) {
            long graphIn = ChannelAttribution.countOfChannel(before, index, SearchChannelType.GRAPH);
            long graphOut = ChannelAttribution.countOfChannel(after, index, SearchChannelType.GRAPH);
            log.info("检索归因 - 图谱证据存活: {}/{}", graphOut, graphIn);
        }
    }
}
