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
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalBudget;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.vector.VectorRetrieverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorSearchChannelTest {

    private static final String QUESTION = "报销发票贴哪张表？";
    private static final List<String> SUPPLEMENT = List.of("kb-hr", "kb-tech");
    private static final float[] QUERY_VECTOR = {0.6F, 0.8F};
    /** Production config: recall 20 per channel, rerank candidate pool 40, final 10 */
    private static final RetrievalBudget PRODUCTION_BUDGET = new RetrievalBudget(20, 40, 10);

    private VectorRetrieverService retrieverService;
    private SearchChannelProperties properties;

    @BeforeEach
    void setUp() {
        retrieverService = mock(VectorRetrieverService.class);
        when(retrieverService.embedAndNormalize(QUESTION)).thenReturn(QUERY_VECTOR);
        when(retrieverService.supportsGlobalRetrieval()).thenReturn(true);
        // Stub that honors topK: a real backend's result count is bounded by the requested depth, or a mismatch such as "primary capacity < quota" cannot be seen
        when(retrieverService.retrieveByVector(any(float[].class), any(RetrieveRequest.class)))
                .thenAnswer(invocation -> {
                    RetrieveRequest request = invocation.getArgument(1);
                    return roundRobin(request.getEffectiveCollectionNames(), request.getTopK());
                });
        properties = new SearchChannelProperties();
    }

    @Test
    @DisplayName("定向作用域下并行补一路未命中库，占通道产能的保底名额")
    void supplementQueryTakesReservedQuota() {
        search(directedScope(), PRODUCTION_BUDGET);

        RetrieveRequest supplement = captureRequests().stream()
                .filter(request -> request.getEffectiveCollectionNames().equals(SUPPLEMENT))
                .findFirst()
                .orElseThrow();
        assertEquals(5, supplement.getTopK(), "单意图产能 20，补充路应拿其中 25%");
    }

    @Test
    @DisplayName("主补切分只由通道召回额度与比例决定，不随意图数与候选池上限漂移")
    void supplementShareStaysConstantAcrossIntentCountAndCandidateLimit() {
        // After the directed path became a per-collection fetch, channel output quota is always recallBudget, the same definition as the keyword and graph channels.
        // The old capacity was intent-count times recallBudget, which drifted the supplement share from 25% to 56%
        assertSupplementShare(1, 40, 15, 5);
        assertSupplementShare(2, 40, 15, 5);
        assertSupplementShare(3, 40, 15, 5);
        assertSupplementShare(1, 100, 15, 5);
    }

    @Test
    @DisplayName("意图节点自定义 topK 时覆盖召回深度，且受候选池上限钳制")
    void customNodeTopKOverridesDepthClampedByCandidateLimit() {
        NodeScore deepIntent = NodeScore.builder()
                .node(IntentNode.builder()
                        .id("finance")
                        .name("财务")
                        .collectionNames(List.of("kb-finance"))
                        .topK(100)
                        .build())
                .score(0.9)
                .build();
        RetrievalScope scope = new RetrievalScope(
                true, 0.9, List.of(deepIntent), List.of("kb-finance"), SUPPLEMENT);

        List<RetrievedChunk> chunks = search(scope, PRODUCTION_BUDGET).getChunks();

        // Depth 100 is clamped to the candidate-pool cap of 40 and then split. A deep intent is not wrongly cut to 15, and the part past the pool is not fetched for nothing
        assertEquals(30, count(chunks, "dir"));
        assertEquals(10, count(chunks, "sup"));
    }

    @Test
    @DisplayName("多意图命中时召回深度取各意图的最大值，缺省者按通道额度参与比较")
    void multiIntentDepthTakesMaxWithRecallBudgetDefault() {
        NodeScore shallow = NodeScore.builder()
                .node(IntentNode.builder()
                        .id("faq").name("FAQ")
                        .collectionNames(List.of("kb-faq"))
                        .topK(5)
                        .build())
                .score(0.9)
                .build();
        RetrievalScope scope = new RetrievalScope(true, 0.9,
                List.of(shallow, intent("kb-finance")),
                List.of("kb-faq", "kb-finance"), SUPPLEMENT);

        List<RetrievedChunk> chunks = search(scope, PRODUCTION_BUDGET).getChunks();

        // max(5, default 20) = 20: one query has one depth. Taking the larger value only widens recall and leaves narrowing to rerank
        assertEquals(15, count(chunks, "dir"));
        assertEquals(5, count(chunks, "sup"));
    }

    @Test
    @DisplayName("单意图 topK 小于通道额度时按绝对深度收窄")
    void smallNodeTopKNarrowsDepthAbsolutely() {
        NodeScore shallow = NodeScore.builder()
                .node(IntentNode.builder()
                        .id("faq").name("FAQ")
                        .collectionNames(List.of("kb-faq"))
                        .topK(5)
                        .build())
                .score(0.9)
                .build();
        RetrievalScope scope = new RetrievalScope(
                true, 0.9, List.of(shallow), List.of("kb-faq"), SUPPLEMENT);

        List<RetrievedChunk> chunks = search(scope, PRODUCTION_BUDGET).getChunks();

        // topK is an absolute depth, not a floor: a small collection configured at 5 recalls only 5, then splits into primary 4 and supplement 1
        assertEquals(4, count(chunks, "dir"));
        assertEquals(1, count(chunks, "sup"));
    }

    @Test
    @DisplayName("命中库已覆盖全部有效库时不补充")
    void fullCoverageSkipsSupplementQuery() {
        RetrievalScope scope = new RetrievalScope(
                true, 0.9, List.of(intent("kb-finance")), List.of("kb-finance"), List.of());

        List<RetrievedChunk> chunks = search(scope, PRODUCTION_BUDGET).getChunks();

        assertEquals(List.of(List.of("kb-finance")),
                captureRequests().stream().map(RetrieveRequest::getEffectiveCollectionNames).toList());
        assertEquals(20, chunks.size(), "无处可补时名额全归主路");
    }

    @Test
    @DisplayName("补充比例置零时退化为纯定向")
    void zeroSupplementRatioDisablesSupplement() {
        properties.getScope().setSupplementRatio(0);

        List<RetrievedChunk> chunks = search(directedScope(), PRODUCTION_BUDGET).getChunks();

        assertTrue(captureRequests().stream()
                .noneMatch(request -> request.getEffectiveCollectionNames().equals(SUPPLEMENT)));
        assertEquals(20, chunks.size(), "关闭补充路后主路拿满产能");
    }

    @Test
    @DisplayName("定向路与补充路共用一次Query向量")
    void directedAndSupplementShareOneQueryVector() {
        search(directedScope(), PRODUCTION_BUDGET);

        ArgumentCaptor<float[]> vectorCaptor = ArgumentCaptor.forClass(float[].class);
        verify(retrieverService, times(2)).retrieveByVector(vectorCaptor.capture(), any(RetrieveRequest.class));
        vectorCaptor.getAllValues().forEach(vector -> assertSame(QUERY_VECTOR, vector));
        verify(retrieverService, times(1)).embedAndNormalize(QUESTION);
    }

    @Test
    @DisplayName("全局作用域下单次跨全部有效库检索，取数深度与其他通道同源")
    void globalScopeQueriesAllCollectionsOnce() {
        // The global path's fetch is governed only by recallBudget: the candidate-pool cap is a gate after RRF, not a fetch target.
        // Using it as the fetch count would make "tune the rerank pool" also rewrite the vector-store query depth, one config with two jobs
        RetrievalScope scope = RetrievalScope.global(0.3, List.of("kb-finance", "kb-hr", "kb-tech"));

        search(scope, PRODUCTION_BUDGET);

        List<RetrieveRequest> requests = captureRequests();
        assertEquals(1, requests.size());
        assertEquals(List.of("kb-finance", "kb-hr", "kb-tech"), requests.get(0).getEffectiveCollectionNames());
        assertEquals(20, requests.get(0).getTopK());
    }

    @Test
    @DisplayName("不同意图节点绑定同一个库时只发一次查询")
    void intentsOnSameCollectionShareOneQuery() {
        // The query is per collection set and no longer fans out by intent: several intents on the same collection can produce only one query.
        // Under the old mechanism this needed "query-identity dedup", or the same chunk occupied two ranks and was double-counted by RRF
        RetrievalScope scope = new RetrievalScope(true, 0.9,
                List.of(intent("报销", "kb-finance"), intent("发票", "kb-finance")),
                List.of("kb-finance"), SUPPLEMENT);

        List<RetrievedChunk> chunks = search(scope, PRODUCTION_BUDGET).getChunks();

        assertEquals(1, captureRequests().stream()
                .filter(request -> request.getEffectiveCollectionNames().equals(List.of("kb-finance")))
                .count());
        assertEquals(15, count(chunks, "dir"));
        assertEquals(5, count(chunks, "sup"));
    }

    @Test
    @DisplayName("意图库范围部分重叠时通道出口无重复名次")
    void overlappingIntentCollectionsProduceNoDuplicateRanks() {
        // Two nodes overlap in part ([finance, policy] and [finance]). The primary path fetches the union of hit collections, each collection once,
        // The old mechanism queried once per intent, so an overlapping chunk occupied two ranks and needed a distinct at the exit — that risk left with the mechanism
        RetrievalScope scope = new RetrievalScope(true, 0.9,
                List.of(intent("综合", "kb-finance", "kb-policy"), intent("报销", "kb-finance")),
                List.of("kb-finance", "kb-policy"), List.of());

        List<RetrievedChunk> chunks = search(scope, PRODUCTION_BUDGET).getChunks();

        assertEquals(1, captureRequests().size(), "并集单查，范围重叠不再产生重复查询");
        assertEquals(chunks.stream().map(RetrievedChunk::getId).distinct().count(), chunks.size(),
                "同一 chunk 占两个名次会被下游 RRF 按名次累加成双倍分");
        assertEquals(20, chunks.size(), "命中库覆盖全部有效库时无补充，主路拿满通道额度");
    }

    @Test
    @DisplayName("后端不支持跨库单查时补充路名额仍被兑现")
    void fanOutFallbackStillHonoursSupplementQuota() {
        // Regression: under the fan-out fallback the budget is a per-collection cap. Without truncation, 2 supplement collections inflate 5 slots into 10 items.
        // Both current backends support a single cross-collection query, so this does not fire today, but the interface default is false and a new backend that does not override it will hit it
        when(retrieverService.supportsGlobalRetrieval()).thenReturn(false);

        List<RetrievedChunk> chunks = search(directedScope(), PRODUCTION_BUDGET).getChunks();

        assertEquals(5, count(chunks, "sup"), "补充路名额是总量，不随补充库数放大");
        assertEquals(15, count(chunks, "dir"));
    }

    @Test
    @DisplayName("补充路失败时定向证据仍然产出")
    void supplementFailureDoesNotDropDirectedChunks() {
        // Regression: the supplement path holds the fallback quota, but if it throws, directed evidence already fetched is dropped with it by the channel-level catch,
        // which means the fallback path takes the primary path with it — the opposite of robustness
        when(retrieverService.retrieveByVector(any(float[].class), any(RetrieveRequest.class)))
                .thenAnswer(invocation -> {
                    RetrieveRequest request = invocation.getArgument(1);
                    if (request.getEffectiveCollectionNames().equals(SUPPLEMENT)) {
                        throw new IllegalStateException("向量库抖动");
                    }
                    return roundRobin(request.getEffectiveCollectionNames(), request.getTopK());
                });

        List<RetrievedChunk> chunks = search(directedScope(), PRODUCTION_BUDGET).getChunks();

        assertEquals(15, count(chunks, "dir"), "补充路故障只该损失补充证据");
        assertEquals(0, count(chunks, "sup"));
    }

    @Test
    @DisplayName("后端返回乱序时通道出口仍按相关性降序")
    void backendOutOfOrderIsResortedAtChannelExit() {
        // Regression: PG enables hnsw.iterative_scan=relaxed_order. pgvector in that mode allows slight disorder and the planner does not add a Sort,
        // and the global path used to return the backend list as-is, the only channel exit that did not re-sort
        when(retrieverService.retrieveByVector(any(float[].class), any(RetrieveRequest.class)))
                .thenReturn(List.of(chunk("a", 0.5F), chunk("b", 0.9F), chunk("c", 0.7F)));

        List<RetrievedChunk> chunks = search(
                RetrievalScope.global(0.3, List.of("kb-finance")), PRODUCTION_BUDGET).getChunks();

        assertEquals(List.of("b", "c", "a"), chunks.stream().map(RetrievedChunk::getId).toList(),
                "下游 RRF 按列表位次取分，出口乱序等于名次基准失真");
    }

    /**
     * Assert the actual primary-path and supplement-path output counts for a given intent count and candidate-pool cap
     */
    private void assertSupplementShare(int intentCount, int candidateLimit, int expectedPrimary, int expectedSupplement) {
        setUp();
        List<NodeScore> intents = IntStream.range(0, intentCount)
                .mapToObj(index -> intent("kb-" + index))
                .toList();
        List<String> targets = intents.stream()
                .map(nodeScore -> nodeScore.getNode().getCollectionNames().get(0))
                .toList();
        RetrievalScope scope = new RetrievalScope(true, 0.9, intents, targets, SUPPLEMENT);

        List<RetrievedChunk> chunks = search(scope, new RetrievalBudget(20, candidateLimit, 10)).getChunks();

        String label = String.format("意图数=%d，候选池上限=%d", intentCount, candidateLimit);
        assertEquals(expectedPrimary, count(chunks, "dir"), label + " 的主路条数");
        assertEquals(expectedSupplement, count(chunks, "sup"), label + " 的补充条数");
    }

    private static long count(List<RetrievedChunk> chunks, String prefix) {
        return chunks.stream().filter(chunk -> chunk.getId().startsWith(prefix)).count();
    }

    private SearchChannelResult search(RetrievalScope scope, RetrievalBudget budget) {
        SearchContext context = SearchContext.builder()
                .originalQuestion(QUESTION)
                .budget(budget)
                .retrievalScope(scope)
                .build();
        return new VectorSearchChannel(retrieverService, properties, Runnable::run).search(context);
    }

    private List<RetrieveRequest> captureRequests() {
        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(retrieverService, atLeastOnce()).retrieveByVector(any(float[].class), captor.capture());
        return captor.getAllValues();
    }

    private static RetrievalScope directedScope() {
        return new RetrievalScope(true, 0.9, List.of(intent("kb-finance")), List.of("kb-finance"), SUPPLEMENT);
    }

    private static NodeScore intent(String collection) {
        return intent(collection, collection);
    }

    /**
     * Intent id is decoupled from collection name: in a real config two different intents can share one collection, or overlap in part
     */
    private static NodeScore intent(String id, String... collections) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id(id)
                        .name(id)
                        .collectionNames(List.of(collections))
                        .build())
                .score(0.9)
                .build();
    }

    private static RetrievedChunk chunk(String id, float score) {
        return RetrievedChunk.builder().id(id).text(id).score(score).build();
    }

    /**
     * Fill topK by rotating across collections
     * <p>
     * A chunk's id and score may depend only on the collection it belongs to, not on the query range: two queries with overlapping ranges should return overlapping chunks,
     * and per-collection fan-out and one cross-collection query should return the same chunks. If the stub derived the id from "whether this is the supplement path",
     * "an overlapping range occupying two ranks" and "quota inflated under fan-out" would stay invisible in the tests
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
                            .score((supplement ? 0.5F : 0.9F) - index * 0.001F)
                            .build();
                })
                .toList();
    }
}
