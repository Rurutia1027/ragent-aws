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
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.keyword.KeywordRetrieverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Keyword retrieval channel
 * <p>
 * BM25 keyword recall on a full-text engine (ES), complementary to the vector channel: strong on exact terms, identifiers, and proper nouns.
 * Registered only when ES keyword search is enabled (rag.keyword.type=es);
 * otherwise the channel does not exist and the engine degrades to vector-only retrieval
 * <p>
 * Runs in parallel with the other channels. Results enter RRF fusion together. Channels have no order or priority. The search range reads the scope the engine already resolved, the same source as the vector channel
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.keyword", name = "type", havingValue = "es")
public class KeywordSearchChannel implements SearchChannel {

    private final KeywordRetrieverService keywordRetriever;
    private final SearchChannelProperties properties;
    private final Executor innerRetrievalExecutor;

    @Override
    public String getName() {
        return "KeywordSearch";
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.KEYWORD;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return properties.getChannels().getKeyword().isEnabled();
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();
        try {
            // Scope is resolved once by the engine: directed means hit collections, global means every valid collection
            RetrievalScope scope = context.getRetrievalScope();
            List<String> collections = scope.targetCollections();
            if (CollUtil.isEmpty(collections)) {
                log.info("关键词检索未解析到目标知识库，跳过");
                return emptyResult(System.currentTimeMillis() - startTime);
            }

            String question = context.getMainQuestion();
            ScopeQuota quota = ScopeQuota.split(scope, context.getBudget().recallBudget(), supplementRatio());
            // A supplement-path failure must lose only itself: it holds the fallback quota, and a thrown join() would drop hit-collection evidence already fetched
            // together in the channel-level catch. The current ES implementation happens to swallow the exception, but that is an accident of the implementation, not a channel guarantee
            CompletableFuture<List<RetrievedChunk>> supplementTask = quota.supplement() > 0
                    ? CompletableFuture.supplyAsync(
                    () -> keywordRetriever.search(question, scope.supplementCollections(), quota.supplement()),
                    innerRetrievalExecutor)
                    .exceptionally(e -> {
                        log.warn("关键词补充路检索失败，仅丢弃补充证据: {}", e.getMessage());
                        return List.of();
                    })
                    : CompletableFuture.completedFuture(List.of());

            List<RetrievedChunk> primary = keywordRetriever.search(question, collections, quota.primary());
            List<RetrievedChunk> supplement = supplementTask.join();

            long latency = System.currentTimeMillis() - startTime;
            log.info("关键词检索完成，命中 {} 库 {} 条，补充 {} 库 {} 条，耗时 {}ms",
                    collections.size(), primary.size(), scope.supplementCollections().size(), supplement.size(), latency);

            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.KEYWORD)
                    .channelName(getName())
                    .chunks(ChunkRanking.mergeByScore(primary, supplement))
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            log.error("关键词检索失败", e);
            return emptyResult(System.currentTimeMillis() - startTime);
        }
    }

    private double supplementRatio() {
        return properties.getScope().getSupplementRatio();
    }
}
