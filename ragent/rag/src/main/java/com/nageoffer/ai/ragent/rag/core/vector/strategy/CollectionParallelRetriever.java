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

package com.nageoffer.ai.ragent.rag.core.vector.strategy;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.vector.VectorRetrieverService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Per-collection parallel retriever
 * <p>
 * Fallback fetch path when the backend cannot search across collections in one query: fetch each given collection in parallel, then merge and sort.
 * One collection failing loses only that collection's results
 */
@Slf4j
public class CollectionParallelRetriever {

    private final VectorRetrieverService retrieverService;
    private final Executor executor;

    public CollectionParallelRetriever(VectorRetrieverService retrieverService,
                                       Executor executor) {
        this.retrieverService = retrieverService;
        this.executor = executor;
    }

    /**
     * Parallel retrieval. Builds the query vector internally
     */
    public List<RetrievedChunk> executeParallelRetrieval(String question,
                                                         List<String> collections,
                                                         int topK) {
        return executeParallelRetrieval(question, collections, topK, retrieverService.embedAndNormalize(question));
    }

    /**
     * Parallel retrieval, reusing a query vector the caller already computed.
     * Used when the same request has another vector fetch path (for example the vector channel's supplement path) so they share one embedding
     *
     * @param queryVector normalized query vector
     */
    public List<RetrievedChunk> executeParallelRetrieval(String question,
                                                         List<String> collections,
                                                         int topK,
                                                         float[] queryVector) {
        record RetrievalFuture(String collection, CompletableFuture<List<RetrievedChunk>> future) {
        }

        List<RetrievalFuture> futures = collections.stream()
                .map(collection -> new RetrievalFuture(collection, CompletableFuture.supplyAsync(
                        () -> retrieveOne(question, collection, queryVector, topK),
                        executor
                )))
                .toList();

        List<RetrievedChunk> allChunks = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (RetrievalFuture future : futures) {
            try {
                allChunks.addAll(future.future().join());
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("全局检索 获取检索结果失败 - Collection: {}", future.collection(), e);
            }
        }

        // Each collection's parallel sub-list is ordered only within itself. After concatenation, cross-collection rank equals concatenation order,
        // which would distort the rank baseline of downstream truncation and RRF, so the exit sorts by score descending
        allChunks.sort(RetrievedChunk.BY_SCORE_DESC);

        log.info("全局检索 检索统计 - 总目标数: {}, 成功: {}, 失败: {}, 检索到 Chunk 总数: {}",
                collections.size(), successCount, failureCount, allChunks.size());

        return allChunks;
    }

    /**
     * Fetch one collection. On failure return an empty list, honoring "one collection failing loses only itself"
     */
    private List<RetrievedChunk> retrieveOne(String question, String collectionName, float[] queryVector, int topK) {
        try {
            return retrieverService.retrieveByVector(
                    queryVector,
                    RetrieveRequest.builder()
                            .collectionName(collectionName)
                            .query(question)
                            .topK(topK)
                            .build()
            );
        } catch (Exception e) {
            log.error("在 collection {} 中检索失败，错误: {}", collectionName, e.getMessage(), e);
            return List.of();
        }
    }
}
