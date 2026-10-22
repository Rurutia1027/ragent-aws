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

import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Retrieval scope
 * <p>
 * Computed once per sub-question and shared by vector, keyword, and graph: if the KB intent is confident enough, narrow to the hit collections (directed); otherwise fall back to all collections (global).
 * Under directed scope, {@code supplementCollections} is the miss set. The vector channel searches it in parallel to cover misses caused by a wrong intent
 *
 * @param directed              whether to narrow to the hit collections
 * @param topScore              highest KB-intent score, for observation and threshold calibration only
 * @param intents               hit KB intents; non-empty when directed
 * @param targetCollections     primary range: directed is the hit collections that are still valid (bound set intersected with the knowledge-base table); global is every valid collection
 * @param supplementCollections supplement range: every valid collection minus the hit collections; always empty under a global scope
 */
public record RetrievalScope(boolean directed,
                             double topScore,
                             List<NodeScore> intents,
                             List<String> targetCollections,
                             List<String> supplementCollections) {

    public Set<String> directedIntentIds() {
        if (!directed) {
            return Set.of();
        }
        Set<String> intentIds = new LinkedHashSet<>();
        for (NodeScore intent : intents) {
            if (intent == null || intent.getNode() == null) {
                continue;
            }
            String intentId = intent.getNode().getId();
            if (intentId == null || intentId.isBlank()) {
                continue;
            }
            intentIds.add(intentId);
        }
        return Set.copyOf(intentIds);
    }

    /**
     * Global scope: do not narrow, no supplement path
     */
    public static RetrievalScope global(double topScore, List<String> activeCollections) {
        return new RetrievalScope(false, topScore, List.of(), activeCollections, List.of());
    }
}
