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
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunkKey;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelType;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fusion post-processor (RRF)
 * <p>
 * Merges several retrieval channels with Reciprocal Rank Fusion
 * Vector scores (cosine) and keyword scores (BM25) have different scales and cannot be compared directly. RRF uses only rank, so it is comparable across modalities
 * <p>
 * score(chunk) = Σ_channel weight_channel / (k + rank_channel)
 * <p>
 * weight_channel is each channel's contribution weight ({@code fusion.channel-weights}). After RRF drops score scale, channels would otherwise be equal.
 * Weights give channels of different trust different say (for example a newly added graph channel running on the global graph is down-weighted), so a noisy channel cannot take the front ranks by rank alone
 * <p>
 * Rank comes from the immutable {@link SearchChannelResult} list (each channel's original recall order),
 * so even after an upstream dedup processor has merged chunks, "hit on several paths" is not lost
 * <p>
 * After fusion ranking, the candidate pool is truncated by rerankCandidateLimit and only the top N by score go downstream to rerank:
 * that bounds rerank cost and latency, and lets candidates hit on several paths enter first by RRF score,
 * so the two-stage split of coarse rank (here) plus fine rank (rerank) actually holds
 * <p>
 * Placed after dedup (order=1) and before rerank (order=10). With a single channel, fusion is skipped and only truncation runs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FusionPostProcessor implements SearchResultPostProcessor {

    private static final String STRATEGY_RRF = "rrf";

    private final SearchChannelProperties properties;

    @Override
    public String getName() {
        return "Fusion";
    }

    @Override
    public int getOrder() {
        return 5;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return STRATEGY_RRF.equalsIgnoreCase(properties.getFusion().getStrategy());
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        // RRF fusion re-rank only when there are multiple channels; a single channel keeps its original recall order
        List<RetrievedChunk> ranked = results != null && results.size() > 1
                ? fuseByRrf(chunks, results)
                : chunks;

        // Truncate the candidate pool: keep only the top N by score for rerank, bounding its cost and latency
        return truncateForRerank(ranked, results, context.getBudget().candidateLimit());
    }

    /**
     * Accumulate RRF scores from each channel's original recall rank, write them back onto the deduped chunks, and sort by score descending
     */
    private List<RetrievedChunk> fuseByRrf(List<RetrievedChunk> chunks, List<SearchChannelResult> results) {
        int k = properties.getFusion().getRrfK();

        Map<String, Double> rrfScores = new LinkedHashMap<>();
        for (SearchChannelResult result : results) {
            double weight = weightOf(result.getChannelType());
            List<RetrievedChunk> channelChunks = result.getChunks();
            for (int rank = 0; rank < channelChunks.size(); rank++) {
                String key = RetrievedChunkKey.of(channelChunks.get(rank));
                double delta = weight / (k + rank + 1);
                rrfScores.merge(key, delta, Double::sum);
            }
        }

        List<RetrievedChunk> fused = new ArrayList<>(chunks);
        for (RetrievedChunk chunk : fused) {
            Double score = rrfScores.get(RetrievedChunkKey.of(chunk));
            chunk.setScore(score != null ? score.floatValue() : 0f);
        }
        fused.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return fused;
    }

    /**
     * Truncate to the candidate-pool cap and keep only the first N for downstream rerank.
     * limit <= 0 means do not truncate (pass everything through)
     */
    private List<RetrievedChunk> truncateForRerank(List<RetrievedChunk> ranked, List<SearchChannelResult> results, int limit) {
        boolean truncate = limit > 0 && ranked.size() > limit;
        List<RetrievedChunk> candidates = truncate
                ? new ArrayList<>(ranked.subList(0, limit))
                : ranked;

        int channelCount = results == null ? 0 : results.size();
        log.info("RRF 融合完成 - 通道数: {}, k: {}, 融合后: {} 个, 截断上限: {}, 送入 Rerank: {} 个",
                channelCount, properties.getFusion().getRrfK(), ranked.size(),
                limit > 0 ? String.valueOf(limit) : "不限", candidates.size());

        // Truncation is the first place evidence from a weak channel disappears: log the in-pool and out-of-pool distributions side by side, so a channel cut away entirely is visible here.
        // The survival log of downstream rerank already sees an input of 0
        if (channelCount > 1) {
            Map<String, Set<SearchChannelType>> index = ChannelAttribution.index(results);
            log.info("检索归因 - 融合池按通道: {}, 送入 Rerank 按通道: {}",
                    ChannelAttribution.format(ChannelAttribution.countByChannel(ranked, index)),
                    ChannelAttribution.format(ChannelAttribution.countByChannel(candidates, index)));
        }
        return candidates;
    }

    /**
     * Per-channel RRF contribution weight: channels of different trust get different say during fusion.
     * The config layer does not depend on the core channel enum, so the enum-to-weight mapping lives here
     */
    private double weightOf(SearchChannelType type) {
        SearchChannelProperties.ChannelWeights w = properties.getFusion().getChannelWeights();
        return switch (type) {
            case VECTOR -> w.getVector();
            case KEYWORD -> w.getKeyword();
            case GRAPH -> w.getGraph();
            case WEB_SEARCH -> w.getWebSearch();
            case HYBRID -> w.getDefaultWeight();
        };
    }
}
