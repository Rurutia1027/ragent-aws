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

import java.util.ArrayList;
import java.util.List;

/**
 * Rank cleanup at the channel exit
 * <p>
 * "Channel exit is ordered by relevance" is the invariant downstream RRF depends on when it scores by rank. The three KB channels share this implementation,
 * so a rule change lands in one place and cannot be missed in a copied copy
 */
public final class ChunkRanking {

    private ChunkRanking() {
    }

    /**
     * Merge primary-path and supplement-path candidates and sort by relevance descending
     * <p>
     * "Ordered at the exit" is a contract this method fulfills, not a precondition on the input — when the supplement path is empty the primary path is still re-sorted,
     * and a backend that returns an unordered list (for example PG relaxed_order) is covered. Do not put the primary path first and append the supplement path: both paths share a score scale,
     * so concatenation would pin a strong supplement hit behind a weak primary hit. RRF scores by rank, which would grant the quota and then sort it away
     */
    public static List<RetrievedChunk> mergeByScore(List<RetrievedChunk> primary, List<RetrievedChunk> supplement) {
        if (supplement.isEmpty()) {
            return sortedByScore(primary);
        }
        List<RetrievedChunk> merged = new ArrayList<>(primary.size() + supplement.size());
        merged.addAll(primary);
        merged.addAll(supplement);
        merged.sort(RetrievedChunk.BY_SCORE_DESC);
        return merged;
    }

    /**
     * Return a copy sorted by relevance descending. If the input has fewer than two items, return it unchanged
     */
    public static List<RetrievedChunk> sortedByScore(List<RetrievedChunk> chunks) {
        if (chunks.size() < 2) {
            return chunks;
        }
        List<RetrievedChunk> sorted = new ArrayList<>(chunks);
        sorted.sort(RetrievedChunk.BY_SCORE_DESC);
        return sorted;
    }

    /**
     * Highest score on one candidate path, for threshold-calibration observation. An empty list is 0
     */
    public static float topScoreOf(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return 0F;
        }
        Float score = chunks.get(0).getScore();
        return score == null ? Float.NEGATIVE_INFINITY : score;
    }
}
