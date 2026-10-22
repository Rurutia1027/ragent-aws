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

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalBudget;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.vector.VectorRetrieverService;
import com.nageoffer.ai.ragent.rag.core.vector.strategy.CollectionParallelRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Vector retrieval channel
 * <p>
 * Vector modality is one channel: directed and global are the same embedding query and differ only in collection range.
 * Splitting them into two parallel channels would let the same evidence weight itself in RRF
 * <p>
 * Under a directed scope, a parallel "miss collections" path is added: when the intent is wrong, the correct evidence is only in the miss collections.
 * Whether the intent is wrong cannot be judged reliably beforehand (intent scores are uncalibrated) or measured afterwards (cosine of the wrong collection is not necessarily low),
 * so there is no judgment — the supplement path gets a fixed candidate quota and is sent downstream with the directed path for reranking
 */
@Slf4j
@Component
public class VectorSearchChannel implements SearchChannel {

    private final SearchChannelProperties properties;
    private final VectorRetrieverService retrieverService;
    private final CollectionParallelRetriever globalRetriever;
    private final Executor retrievalExecutor;

    public VectorSearchChannel(VectorRetrieverService retrieverService,
                               SearchChannelProperties properties,
                               Executor innerRetrievalExecutor) {
        this.properties = properties;
        this.retrieverService = retrieverService;
        this.globalRetriever = new CollectionParallelRetriever(retrieverService, innerRetrievalExecutor);
        this.retrievalExecutor = innerRetrievalExecutor;
    }

    @Override
    public String getName() {
        return "VectorSearch";
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        // One channel, one switch; once enabled there is always a scope to run
        return properties.getChannels().getVector().isEnabled();
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();

        try {
            RetrievalScope scope = context.getRetrievalScope();
            List<RetrievedChunk> chunks;
            Map<String, Object> metadata;
            if (scope.directed()) {
                chunks = retrieveDirected(context, scope);
                metadata = Map.of("scope", "directed", "topScore", scope.topScore());
            } else {
                chunks = retrieveGlobal(context, scope);
                metadata = Map.of("scope", "global", "topScore", scope.topScore());
            }

            long latency = System.currentTimeMillis() - startTime;
            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.VECTOR)
                    .channelName(getName())
                    .chunks(chunks)
                    .latencyMs(latency)
                    .metadata(metadata)
                    .build();

        } catch (Exception e) {
            log.error("向量检索失败", e);
            return emptyResult(System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.VECTOR;
    }

    /**
     * Directed scope: take primary candidates from the hit collections and, in parallel, supplement from the miss collections.
     * Directed and global share one fetch primitive and differ only in the collection set. Both paths share one embedding and the same pool, so the supplement path adds no channel latency
     */
    private List<RetrievedChunk> retrieveDirected(SearchContext context, RetrievalScope scope) {
        String question = context.getMainQuestion();
        float[] queryVector = retrieverService.embedAndNormalize(question);
        ScopeQuota quota = ScopeQuota.split(scope, resolveDirectedBudget(scope, context.getBudget()), supplementRatio());

        // A supplement-path failure must lose only itself: it holds the fallback quota, and a thrown join() would drop directed evidence already fetched
        // in the channel-level catch — the fallback path would take the primary path with it, which is the opposite of robustness
        CompletableFuture<List<RetrievedChunk>> supplementTask = quota.supplement() > 0
                ? CompletableFuture.<List<RetrievedChunk>>supplyAsync(
                () -> retrieveOver(question, queryVector, scope.supplementCollections(), quota.supplement()),
                retrievalExecutor)
                .exceptionally(e -> {
                    log.warn("向量补充路检索失败，仅丢弃补充证据: {}", e.getMessage());
                    return List.of();
                })
                : CompletableFuture.completedFuture(List.of());

        List<RetrievedChunk> directed = retrieveOver(question, queryVector, scope.targetCollections(), quota.primary());
        List<RetrievedChunk> supplement = supplementTask.join();

        log.info("向量检索完成（定向），意图 top1={}，命中 {} 库 {} 条（最高余弦 {}），补充 {} 库 {} 条（最高余弦 {}）",
                scope.topScore(), scope.targetCollections().size(), directed.size(), ChunkRanking.topScoreOf(directed),
                scope.supplementCollections().size(), supplement.size(), ChunkRanking.topScoreOf(supplement));
        return ChunkRanking.mergeByScore(directed, supplement);
    }

    /**
     * Output quota of the directed path: the intent-level node.topK overrides the per-channel default recallBudget. It is an absolute depth and may be larger or smaller
     * <p>
     * After per-collection fetch, one query has one depth. When several intents hit, take the max — a larger depth only widens recall, and the extra candidates are narrowed by rerank.
     * Any scheme that splits quota by intent rebuilds fan-out. Then clamp by the candidate-pool cap: anything beyond it cannot enter rerank, so fetching it is wasted work
     */
    private int resolveDirectedBudget(RetrievalScope scope, RetrievalBudget budget) {
        int depth = scope.intents().stream()
                .mapToInt(nodeScore -> {
                    Integer topK = nodeScore.getNode() == null ? null : nodeScore.getNode().getTopK();
                    return topK != null && topK > 0 ? topK : budget.recallBudget();
                })
                .max()
                .orElse(budget.recallBudget());
        int candidateLimit = budget.candidateLimit();
        return candidateLimit > 0 ? Math.min(depth, candidateLimit) : depth;
    }

    /**
     * Global scope: search across every valid collection.
     * Fetch depth shares its source with the other channels and is governed only by recallBudget — the candidate-pool cap is a gate after RRF, not a fetch target
     */
    private List<RetrievedChunk> retrieveGlobal(SearchContext context, RetrievalScope scope) {
        if (scope.targetCollections().isEmpty()) {
            log.warn("未找到任何 KB collection，跳过全局检索");
            return List.of();
        }
        String question = context.getMainQuestion();
        List<RetrievedChunk> chunks = retrieveOver(question, retrieverService.embedAndNormalize(question),
                scope.targetCollections(), context.getBudget().recallBudget());

        log.info("向量检索完成（全局），意图 top1={}，{} 库 {} 条（最高余弦 {}）",
                scope.topScore(), scope.targetCollections().size(), chunks.size(), ChunkRanking.topScoreOf(chunks));
        return chunks;
    }

    /**
     * Fetch one candidate path inside the given collection range: relevance descending, count at most budget
     * <p>
     * When the backend supports cross-collection filtering (PG / Milvus shared store), one query with the total budget is enough. Otherwise fall back to per-collection parallel fan-out,
     * take budget from each collection, then truncate once — the extra fetch is so the true global top budget can be chosen (which collection holds the good hits is unknown beforehand).
     * The truncation cannot be skipped: without it, budget quietly changes from "total" to "per-collection cap", and the supplement quota is inflated to collection-count times quota
     * <p>
     * Sort before truncating, and do not trust the backend order: PG enables {@code hnsw.iterative_scan=relaxed_order},
     * and pgvector in that mode allows slight disorder and the planner does not add a Sort node. Sort-then-truncate is what takes the global best budget items
     */
    private List<RetrievedChunk> retrieveOver(String question, float[] queryVector, List<String> collections, int budget) {
        if (collections.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> chunks = retrieverService.supportsGlobalRetrieval()
                ? retrieverService.retrieveByVector(queryVector, RetrieveRequest.builder()
                .collectionNames(collections)
                .query(question)
                .topK(budget)
                .build())
                : globalRetriever.executeParallelRetrieval(question, collections, budget, queryVector);
        return ScopeQuota.cap(ChunkRanking.sortedByScore(chunks), budget);
    }

    private double supplementRatio() {
        return properties.getScope().getSupplementRatio();
    }
}
