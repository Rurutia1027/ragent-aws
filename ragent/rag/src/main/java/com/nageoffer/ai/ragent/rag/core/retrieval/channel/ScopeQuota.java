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

import java.util.List;

/**
 * Split of candidate quota between the primary path and the supplement path
 * <p>
 * The three channels share this rule: each cuts the same fraction of its own output quota for collections that were not hit.
 * Supplement evidence needs a fixed quota rather than free competition with hit-collection evidence — when the intent is wrong, the correct evidence is only in the miss collections,
 * and a relevance-score contest loses to hit-collection evidence that looks similar but answers the wrong question.
 * The quota is honored through the channel exit: admission is guaranteed; surviving downstream fusion truncation is not promised here
 * <p>
 * Both fields are always quotas. There is no "non-positive means uncapped" sentinel: if quota and sentinel share a value space,
 * "supplement quota 0" is read as "supplement uncapped", and the configured intent is the opposite of what runs
 *
 * @param primary    primary-path candidate quota; always >= 1 when the supplement is active
 * @param supplement supplement-path candidate quota; 0 means do not supplement this time
 */
public record ScopeQuota(int primary, int supplement) {

    /**
     * Split a channel's output quota by scope.
     * Global scope, no miss collections (for example a single-collection deployment), ratio set to zero, or nothing left to split: all four skip the supplement and give the whole quota to the primary path
     *
     * @param budget          this channel's output quota
     * @param supplementRatio fraction assigned to the supplement path
     */
    public static ScopeQuota split(RetrievalScope scope, int budget, double supplementRatio) {
        if (!scope.directed() || scope.supplementCollections().isEmpty() || supplementRatio <= 0 || budget <= 0) {
            return new ScopeQuota(budget, 0);
        }
        // Clamp once to [1, budget-1] and cover both ends: the upper bound keeps at least one primary slot — squeezing it to 0 would recall nothing from a high-confidence hit collection
        // which is the opposite of "directed first, supplement as fallback". When only 1 slot remains the upper bound naturally becomes 0 and the split degrades to no supplement
        int supplement = Math.min(budget - 1, Math.max(1, (int) Math.round(budget * supplementRatio)));
        return new ScopeQuota(budget - supplement, supplement);
    }

    /**
     * Truncate candidates already sorted by relevance descending to the quota. A quota of 0 takes zero items
     */
    public static <T> List<T> cap(List<T> chunks, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return chunks.size() > limit ? List.copyOf(chunks.subList(0, limit)) : chunks;
    }
}
