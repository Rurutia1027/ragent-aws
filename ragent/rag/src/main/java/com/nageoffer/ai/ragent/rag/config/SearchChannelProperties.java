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

package com.nageoffer.ai.ragent.rag.config;

import com.nageoffer.ai.ragent.rag.constant.RAGConstant;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG retrieval configuration
 * <p>
 * The retrieval funnel has three independent budgets (see {@code RetrievalBudget}):
 * recall fan-out {@link #recallBudget}, then the Rerank candidate-pool cap {@link Fusion#rerankCandidateLimit}, then the final count {@link #defaultTopK}.
 * They must narrow monotonically. {@link #afterPropertiesSet()} checks that at startup
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.search")
public class SearchChannelProperties implements InitializingBean {

    /**
     * Default number of hits that finally enter the LLM (the contextTopK stage of the retrieval budget)
     * This is the product meaning of topK. A request may override it; otherwise this value is used
     */
    private int defaultTopK = 10;

    /**
     * Recall count per channel (the recallBudget stage of the retrieval budget)
     * Every channel (vector intent path / keyword / graph) recalls this absolute number of candidates, then downstream RRF+Rerank narrows them
     * Must be >= defaultTopK (monotonic funnel, checked at startup). <=0 falls back to defaultTopK as a guard
     */
    private int recallBudget = 20;

    /**
     * Retrieval scope
     * Decides which knowledge bases this request may see. Independent of retrieval modality, so it sits beside channels and every channel reads the same copy
     */
    private Scope scope = new Scope();

    /**
     * Retrieval channel configuration
     */
    private Channels channels = new Channels();

    /**
     * Multi-channel result fusion
     */
    private Fusion fusion = new Fusion();

    /**
     * Evidence relevance gate
     * Decides whether this batch of evidence is good enough for the prompt. Independent of which bases or modalities were queried, so it sits beside scope / fusion
     */
    private Evidence evidence = new Evidence();

    /**
     * Resolve the recall fan-out base: prefer an explicit recallBudget; when unset (<=0), fall back to the final count
     */
    public int resolveRecallBudget(int contextTopK) {
        return recallBudget > 0 ? recallBudget : contextTopK;
    }

    /**
     * Check the funnel monotonicity invariant: recallBudget >= contextTopK and candidateLimit >= contextTopK.
     * A violation means recall is smaller than the final count, or the Rerank candidate pool is smaller than the final count, so Rerank cannot produce enough results.
     * That is a configuration contradiction. Failing at startup is better than silently under-recalling in production
     */
    @Override
    public void afterPropertiesSet() {
        int contextTopK = defaultTopK;
        if (contextTopK <= 0) {
            throw new IllegalStateException("rag.search.default-top-k must be a positive number, current: " + contextTopK);
        }
        int resolvedRecall = resolveRecallBudget(contextTopK);
        if (resolvedRecall < contextTopK) {
            throw new IllegalStateException(String.format(
                    "Search budget invariant broken: recallBudget(%d) < contextTopK(%d). Recall fan-out must not be smaller than the final count, "
                            + "请调大 rag.search.recall-budget 或调小 rag.search.default-top-k",
                    resolvedRecall, contextTopK));
        }
        int candidateLimit = fusion.getRerankCandidateLimit();
        if (candidateLimit > 0 && candidateLimit < contextTopK) {
            throw new IllegalStateException(String.format(
                    "Search budget invariant broken: candidateLimit(%d) < contextTopK(%d). The rerank candidate pool must not be smaller than the final count, "
                            + "请调大 rag.search.fusion.rerank-candidate-limit 或调小 rag.search.default-top-k",
                    candidateLimit, contextTopK));
        }
        if (scope.getMinIntentScore() < RAGConstant.INTENT_MIN_SCORE) {
            throw new IllegalStateException(String.format(
                    "rag.search.scope.min-intent-score(%s) is below the upstream intent filter INTENT_MIN_SCORE(%s), so this setting has no effect, "
                            + "请调高此值，或先下调 INTENT_MIN_SCORE",
                    scope.getMinIntentScore(), RAGConstant.INTENT_MIN_SCORE));
        }
        // The two scope gates must actually be in series: intent is filtered by min-intent-score first, so surviving scores are always >= that floor.
        // If the threshold is not above the minimum score, the low-confidence fallback to global search never fires
        double confidenceThreshold = scope.getConfidenceThreshold();
        if (confidenceThreshold <= scope.getMinIntentScore() || confidenceThreshold > 1) {
            throw new IllegalStateException(String.format(
                    "rag.search.scope.confidence-threshold(%s) must be in (min-intent-score(%s), 1]: "
                            + "不高于最低分则「低置信退化为全局」永不触发，大于 1 则「高置信收窄到命中库」永不触发（意图分按 0~1 输出），"
                            + "两者都会让一整条作用域分支连同补充路一起变成死代码",
                    confidenceThreshold, scope.getMinIntentScore()));
        }
        double supplementRatio = scope.getSupplementRatio();
        if (Double.isNaN(supplementRatio) || supplementRatio >= 1) {
            throw new IllegalStateException(String.format(
                    "rag.search.scope.supplement-ratio(%s) must be less than 1: it is the share moved from the main path to the supplement path, "
                            + "取到 1 等于把高置信命中库的名额清零，与「定向优先、补充兜底」相反；关闭补充路请填 0",
                    supplementRatio));
        }
        // Rerank scores are in 0~1. A floor above 1 drops every piece of evidence, which looks like an empty knowledge base and cannot be told apart in production
        double minRerankScore = evidence.getMinRerankScore();
        if (Double.isNaN(minRerankScore) || minRerankScore > 1) {
            throw new IllegalStateException(String.format(
                    "rag.search.evidence.min-rerank-score(%s) must be <= 1: rerank scores are in 0-1, "
                            + "高于 1 会让全部证据被闸门丢弃、KB 侧恒为空；关闭闸门请填 0",
                    minRerankScore));
        }
    }

    /**
     * Retrieval scope: whether this request narrows to hit knowledge bases or falls back to all of them, and how many reserved slots the misses keep
     * <p>
     * A request-level policy, not a channel parameter. All three channels read the same copy, so disabling any channel does not change scope decisions for the others
     */
    @Data
    public static class Scope {

        /**
         * Minimum intent score
         * Intent nodes below this score are filtered out and do not affect whether the scope is narrowed
         * Upstream {@code IntentResolver} already filters once with {@link RAGConstant#INTENT_MIN_SCORE},
         * so a value below that constant has no effect. Checked at startup (see {@link #afterPropertiesSet()})
         */
        private double minIntentScore = 0.4;

        /**
         * Intent confidence threshold
         * When the top KB intent score is below this threshold, every channel falls back to a full-library search
         */
        private double confidenceThreshold = 0.6;

        /**
         * Reserved share of candidates for the supplement path
         * When scoped, each channel sets aside this fraction of its own quota for knowledge bases that did not hit, covering a wrong intent: on a miss the correct evidence is only in the non-hit bases
         * and cannot win a relevance contest against hit-base evidence, so it gets a fixed quota rather than open competition
         * Must be < 1 (checked at startup): 1 zeros out the main-path quota, which is the opposite of the design
         * <=0 disables the supplement path and leaves pure targeting; it is also skipped when the hit bases already cover every effective base
         */
        private double supplementRatio = 0.25;
    }

    @Data
    public static class Channels {

        /**
         * Per-channel timeout (milliseconds)
         * A channel past this limit is degraded to an empty result; the others still fuse. <=0 means no limit and the fusion waits for the slowest channel
         */
        private long timeoutMs = 15_000;

        /**
         * Vector retrieval configuration
         */
        private Vector vector = new Vector();

        /**
         * Keyword retrieval configuration
         */
        private Keyword keyword = new Keyword();

        /**
         * Web search configuration (You.com Search)
         */
        private WebSearch webSearch = new WebSearch();

        /**
         * Knowledge-graph retrieval configuration
         */
        private Graph graph = new Graph();
    }

    @Data
    public static class Vector {

        /**
         * Whether enabled
         * One master switch for the vector channel; off means no vector recall anywhere
         */
        private boolean enabled = true;
    }

    @Data
    public static class Keyword {

        /**
         * Whether enabled
         * Takes effect only when rag.keyword.type != none (a keyword retrieval implementation exists)
         */
        private boolean enabled = false;
    }

    @Data
    public static class Graph {

        /**
         * Whether enabled
         * Takes effect only when a graph backend is on (rag.graph.type != none)
         */
        private boolean enabled = false;
    }

    @Data
    public static class WebSearch {

        /**
         * Whether enabled
         * Off by default. Turning it on also requires api-key (or the YDC_API_KEY environment variable); missing either leaves the channel inactive
         */
        private boolean enabled = false;

        /**
         * Maximum number of results (web pages + news combined)
         * Default 5, cap 20. You.com is sent a per-section count; after the merge the channel truncates to this value
         */
        private int count = 5;

        /**
         * Request timeout (seconds)
         */
        private int timeoutSeconds = 10;

        /**
         * You.com Search API key
         * Prefer leaving this blank so the YDC_API_KEY environment variable is used and the secret stays out of the config file
         */
        private String apiKey = "";

        /**
         * You.com Search API address
         * Usually left unchanged. Tests may point it at a local stub
         */
        private String apiUrl = "https://ydc-index.io/v1/search";
    }

    @Data
    public static class Fusion {

        /**
         * Fusion strategy
         * rrf is reciprocal-rank fusion (the only implementation today); off disables fusion and passes results through
         */
        private String strategy = "rrf";

        /**
         * RRF smoothing constant k
         * Larger values weaken the advantage of high ranks. The classic value is 60 (for retrieval over thousands of candidates),
         * but this path usually has only about 20~40 candidates per channel, and k=60 flattens rank differences (head and tail scores barely separate).
         * 20 matches the candidate-pool size and keeps the head distinct; tune it against retrieval attribution logs
         */
        private int rrfK = 20;

        /**
         * Rerank candidate cap
         * After RRF ranking, only the top N candidates are sent to Rerank.
         * That bounds Rerank cost and latency, and lets multi-channel hits enter first by RRF score
         * <=0 means no truncation (the full set goes to Rerank). Typical production values are 40~100
         */
        private int rerankCandidateLimit = 40;

        /**
         * Per-channel RRF contribution weights
         * Channels of different trust get different say in fusion. RRF uses rank only and drops score scale, so without weights every channel is equal
         * and a newly added or noisy channel ties the most trusted channel at every rank. With weights, delta = weight / (k + rank)
         */
        private ChannelWeights channelWeights = new ChannelWeights();
    }

    @Data
    public static class ChannelWeights {

        /**
         * Vector weight
         * The vector modality is the most trusted. Intent-scoped and global search share this channel and this weight
         */
        private double vector = 1.0;

        /**
         * Keyword (BM25) weight
         */
        private double keyword = 1.0;

        /**
         * Graph weight
         * The graph channel is new, runs on a single global graph, and its evidence is filtered only on the result side, so it is down-weighted by default.
         * Raise it after attribution logs show its Rerank survival rate. A long-term survival rate of 0 means it is pure cost
         */
        private double graph = 0.5;

        /**
         * Web-search weight
         */
        private double webSearch = 0.5;

        /**
         * Fallback weight for a channel that was not configured explicitly
         */
        private double defaultWeight = 1.0;
    }

    /**
     * Evidence relevance gate
     * Retrieval only promises the N most similar hits. An empty answer still comes back full. The gate adds a relevance floor
     */
    @Data
    public static class Evidence {

        /**
         * Minimum rerank score, 0~1
         * If the best score in the batch is below this value, the whole batch is dropped and treated as no relevant content
         * Not the same scale as the intent score {@link Scope#confidenceThreshold}. Sharing them would make an evidence-filter change also change scope narrowing
         * <=0 disables the gate. When no score is readable (rerank off or degraded to noop), the batch is allowed through
         */
        private double minRerankScore = 0.2;
    }
}
