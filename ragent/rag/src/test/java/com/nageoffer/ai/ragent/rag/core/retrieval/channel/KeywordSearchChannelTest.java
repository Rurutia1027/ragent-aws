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
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.keyword.KeywordRetrieverService;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalBudget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeywordSearchChannelTest {

    private static final String QUESTION = "报销发票贴哪张表？";
    private static final List<String> ACTIVE = List.of("kb-finance", "kb-hr", "kb-tech");
    private static final List<String> SUPPLEMENT = List.of("kb-hr", "kb-tech");
    /** Production config: recall 20 per channel, rerank candidate pool 40, final 10 */
    private static final RetrievalBudget PRODUCTION_BUDGET = new RetrievalBudget(20, 40, 10);

    /**
     * One search issued by the keyword channel, used to assert how quota lands on the actual request
     */
    private record Query(List<String> collections, int topK) {
    }

    private final List<Query> queries = new ArrayList<>();
    private KeywordRetrieverService keywordRetriever;
    private SearchChannelProperties properties;

    @BeforeEach
    void setUp() {
        keywordRetriever = mock(KeywordRetrieverService.class);
        properties = new SearchChannelProperties();
        // Stub that honors topK: a real backend's result count is bounded by the requested depth. A fixed return would hide a quota mismatch
        when(keywordRetriever.search(anyString(), anyList(), anyInt())).thenAnswer(invocation -> {
            List<String> collections = invocation.getArgument(1);
            int topK = invocation.getArgument(2);
            queries.add(new Query(collections, topK));
            return roundRobin(collections, topK);
        });
    }

    @Test
    @DisplayName("定向作用域下主路与补充路各按名额取数")
    void directedScopeSplitsQuotaAcrossBothPaths() {
        List<RetrievedChunk> chunks = search(directedScope(), PRODUCTION_BUDGET);

        assertEquals(List.of(new Query(List.of("kb-finance"), 15), new Query(SUPPLEMENT, 5)),
                sortedQueries(), "产能即 recallBudget 20，切分为主路 15 / 补充路 5");
        assertEquals(15, count(chunks, "dir"));
        assertEquals(5, count(chunks, "sup"));
    }

    @Test
    @DisplayName("命中库已覆盖全部有效库时不补充")
    void fullCoverageSkipsSupplementQuery() {
        RetrievalScope scope = new RetrievalScope(
                true, 0.9, List.of(intent("kb-finance")), ACTIVE, List.of());

        List<RetrievedChunk> chunks = search(scope, PRODUCTION_BUDGET);

        assertEquals(List.of(new Query(ACTIVE, 20)), queries, "无处可补时名额全归主路");
        assertEquals(20, chunks.size());
    }

    @Test
    @DisplayName("补充比例置零时退化为纯定向")
    void zeroSupplementRatioDisablesSupplement() {
        properties.getScope().setSupplementRatio(0);

        List<RetrievedChunk> chunks = search(directedScope(), PRODUCTION_BUDGET);

        assertEquals(List.of(new Query(List.of("kb-finance"), 20)), queries);
        assertEquals(0, count(chunks, "sup"));
    }

    @Test
    @DisplayName("全局作用域下单次跨全部有效库检索")
    void globalScopeQueriesAllCollectionsOnce() {
        List<RetrievedChunk> chunks = search(RetrievalScope.global(0.3, ACTIVE), PRODUCTION_BUDGET);

        assertEquals(List.of(new Query(ACTIVE, 20)), queries, "全局作用域不切分名额");
        assertEquals(20, chunks.size());
    }

    @Test
    @DisplayName("召回额度极小时主路仍取得到数据")
    void tinyRecallBudgetStillFetchesForPrimary() {
        // Regression: this channel uses the quota directly as the ES size rather than truncating afterwards. If the primary quota is squeezed to 0 by the supplement floor,
        // the query issued has size=0 and the primary path gets nothing back (the same 0 means "do not truncate" on the vector and graph channels, with a different consequence)
        List<RetrievedChunk> chunks = search(directedScope(), new RetrievalBudget(1, 40, 1));

        assertTrue(queries.stream().allMatch(query -> query.topK() >= 1),
                "任何一路的取数深度都不该为 0");
        assertEquals(1, chunks.size());
    }

    @Test
    @DisplayName("两路候选合并后按相关性降序")
    void mergedOutputStaysSortedByScore() {
        // A strong supplement hit must be able to rank ahead of a weak primary hit: downstream RRF scores by list position,
        // and primary-first plus supplement-appended grants the quota and then sorts it away
        List<RetrievedChunk> chunks = search(directedScope(), PRODUCTION_BUDGET);

        List<Float> scores = chunks.stream().map(RetrievedChunk::getScore).toList();
        assertEquals(scores.stream().sorted((a, b) -> Float.compare(b, a)).toList(), scores,
                "通道出口须全局有序，否则下游 RRF 的名次基准失真");
    }

    @Test
    @DisplayName("未解析到目标知识库时跳过检索")
    void emptyScopeSkipsRetrieval() {
        List<RetrievedChunk> chunks = search(RetrievalScope.global(0.0, List.of()), PRODUCTION_BUDGET);

        assertTrue(queries.isEmpty());
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("补充路失败时命中库证据仍然产出")
    void supplementFailureDoesNotDropPrimaryChunks() {
        // Regression: the supplement path holds the fallback quota, but if it throws, hit-collection evidence already fetched is dropped with it by the channel-level catch.
        // The current ES implementation happens to swallow the exception, but that is an accident of the implementation, not a channel guarantee
        when(keywordRetriever.search(anyString(), anyList(), anyInt())).thenAnswer(invocation -> {
            List<String> collections = invocation.getArgument(1);
            if (SUPPLEMENT.equals(collections)) {
                throw new IllegalStateException("ES 抖动");
            }
            return roundRobin(collections, invocation.getArgument(2));
        });

        List<RetrievedChunk> chunks = search(directedScope(), PRODUCTION_BUDGET);

        assertEquals(15, count(chunks, "dir"), "补充路故障只该损失补充证据");
        assertEquals(0, count(chunks, "sup"));
    }

    @Test
    @DisplayName("后端异常时降级为空结果而非中断整条链路")
    void retrieverFailureDegradesToEmptyResult() {
        when(keywordRetriever.search(anyString(), anyList(), anyInt()))
                .thenThrow(new IllegalStateException("ES 不可用"));

        assertTrue(search(directedScope(), PRODUCTION_BUDGET).isEmpty());
    }

    private List<RetrievedChunk> search(RetrievalScope scope, RetrievalBudget budget) {
        SearchContext context = SearchContext.builder()
                .originalQuestion(QUESTION)
                .budget(budget)
                .retrievalScope(scope)
                .build();
        return new KeywordSearchChannel(keywordRetriever, properties, Runnable::run)
                .search(context)
                .getChunks();
    }

    /**
     * The two paths are issued concurrently, so list order is not fixed. Normalize to primary-first for the assertion
     */
    private List<Query> sortedQueries() {
        return queries.stream()
                .sorted((left, right) -> Boolean.compare(
                        SUPPLEMENT.containsAll(left.collections()), SUPPLEMENT.containsAll(right.collections())))
                .toList();
    }

    private static long count(List<RetrievedChunk> chunks, String prefix) {
        return chunks.stream().filter(chunk -> chunk.getId().startsWith(prefix)).count();
    }

    private static RetrievalScope directedScope() {
        return new RetrievalScope(
                true, 0.9, List.of(intent("kb-finance")), List.of("kb-finance"), SUPPLEMENT);
    }

    private static NodeScore intent(String collection) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id("intent-" + collection)
                        .name(collection)
                        .collectionNames(List.of(collection))
                        .build())
                .score(0.9)
                .build();
    }

    /**
     * Fill topK by rotating across collections
     * <p>
     * A chunk's id and BM25 score may depend only on the collection it belongs to, not on the query range: in a real backend one chunk belongs to one collection.
     * If the stub derived the id from "whether this is the supplement path", a depth mismatch between the two paths would be invisible in the test.
     * The supplement path's score range overlaps the primary path, to check that the merge is a real sort and not a concatenation
     */
    private static List<RetrievedChunk> roundRobin(List<String> collections, int topK) {
        return IntStream.range(0, topK)
                .mapToObj(index -> {
                    String collection = collections.get(index % collections.size());
                    boolean supplement = SUPPLEMENT.contains(collection);
                    String id = (supplement ? "sup-" : "dir-") + collection + "-" + index / collections.size();
                    return RetrievedChunk.builder()
                            .id(id)
                            .text(id)
                            .score((supplement ? 14.0F : 12.0F) - index)
                            .build();
                })
                .toList();
    }
}
