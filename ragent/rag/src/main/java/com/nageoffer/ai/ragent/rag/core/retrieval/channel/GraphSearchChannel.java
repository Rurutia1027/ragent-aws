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

package com.nageoffer.ai.ragent.rag.core.retrieval.channel;

import cn.hutool.core.collection.CollUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.GraphProperties;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.graph.GraphEvidence;
import com.nageoffer.ai.ragent.rag.core.graph.LightRagClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Knowledge-graph retrieval channel
 * <p>
 * Graph recall based on LightRAG: strong at multi-hop relation reasoning and entity-centric aggregation, complementary to vector and keyword search.
 * Registered only when the graph backend is enabled (rag.graph.type=lightrag); otherwise the channel does not exist and the engine degrades to retrieval without a graph
 * <p>
 * Runs in parallel with the other channels. Results enter RRF fusion together. Channels have no order or priority
 * <p>
 * Note: LightRAG /query has no per-request workspace, so one instance is one graph. This channel queries the whole graph and then splits results by file_path ownership.
 * Real subgraph isolation needs multiple instances and is left for a later stage
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.graph", name = "type", havingValue = "lightrag")
public class GraphSearchChannel implements SearchChannel {

    /**
     * Request-size multiplier sent to LightRAG under directed filtering.
     * Result-side filtering runs after the top_k cutoff and then drops cross-collection evidence, so hit-collection evidence can shrink; fetch extra to recover recall. Global filtering only drops leftovers and does not inflate
     */
    private static final int FILTER_TOPK_BOOST = 3;

    private final LightRagClient lightRagClient;
    private final GraphProperties graphProperties;
    private final SearchChannelProperties properties;

    @Override
    public String getName() {
        return "GraphSearch";
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.GRAPH;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return properties.getChannels().getGraph().isEnabled();
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();
        try {
            // Scope is resolved once by the engine: directed means hit collections, global means every valid collection
            // Global also sends the collection set for result-side filtering: graph deletion is best-effort, so leftovers of a deleted collection can only be stopped here
            RetrievalScope scope = context.getRetrievalScope();
            List<String> collections = scope.targetCollections();
            if (CollUtil.isEmpty(collections)) {
                log.info("图谱检索未解析到有效知识库，跳过");
                return emptyResult(System.currentTimeMillis() - startTime);
            }

            int baseTopK = context.getBudget().recallBudget();
            int topK = scope.directed() ? baseTopK * FILTER_TOPK_BOOST : baseTopK;
            String queryMode = graphProperties.getLightrag().getQueryMode();

            GraphEvidence evidence = lightRagClient.retrieveByScope(
                    context.getMainQuestion(), queryMode, topK, collections);
            if (!scope.directed() && !evidence.unmatched().isEmpty()) {
                log.warn("图谱全局检索过滤掉 {} 条无主证据（已删库残留或解析失败），残留会挤占 top_k 名额，建议清理图谱",
                        evidence.unmatched().size());
            }
            ScopeQuota quota = ScopeQuota.split(scope, baseTopK, properties.getScope().getSupplementRatio());
            List<RetrievedChunk> primary = ScopeQuota.cap(evidence.matched(), quota.primary());
            List<RetrievedChunk> supplement = ScopeQuota.cap(evidence.unmatched(), quota.supplement());

            long latency = System.currentTimeMillis() - startTime;
            log.info("图谱检索完成，范围={}，命中 {} 条，补充 {} 条，耗时 {}ms",
                    scope.directed() ? collections : "全局(" + collections.size() + "库)",
                    primary.size(), supplement.size(), latency);

            // Both candidate lists are scored from one whole-graph rank order; mixing by score restores the graph's own ranking
            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.GRAPH)
                    .channelName(getName())
                    .chunks(ChunkRanking.mergeByScore(primary, supplement))
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            log.error("图谱检索失败", e);
            return emptyResult(System.currentTimeMillis() - startTime);
        }
    }
}
