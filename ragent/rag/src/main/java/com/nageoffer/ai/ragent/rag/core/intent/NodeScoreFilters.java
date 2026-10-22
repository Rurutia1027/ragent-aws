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

package com.nageoffer.ai.ragent.rag.core.intent;

import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NodeScore filter utilities.
 * Filters knowledge-base intents.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class NodeScoreFilters {

    /**
     * Keep KB intents (node non-null, kind null or KB).
     * <p>
     * Does not apply a score floor. The caller must already have filtered by INTENT_MIN_SCORE.
     */
    public static List<NodeScore> kb(List<NodeScore> scores) {
        return scores.stream()
                .filter(ns -> ns.getNode() != null && ns.getNode().isKB())
                .toList();
    }

    /**
     * Keep KB intents at or above a minimum score.
     */
    public static List<NodeScore> kb(List<NodeScore> scores, double minScore) {
        return scores.stream()
                .filter(ns -> ns.getScore() >= minScore)
                .filter(ns -> ns.getNode() != null && ns.getNode().isKB())
                .toList();
    }

    /**
     * Collection names bound to KB intents (drop blanks, dedupe).
     * <p>
     * Shared "intent domain" collection selection for keyword, graph, and similar channels, so the mapping is not repeated.
     */
    public static List<String> kbCollections(List<NodeScore> scores) {
        return kb(scores).stream()
                .flatMap(ns -> ns.getNode().getEffectiveCollectionNames().stream())
                .distinct()
                .toList();
    }
}
