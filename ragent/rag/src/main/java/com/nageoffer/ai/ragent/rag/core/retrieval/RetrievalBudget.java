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

package com.nageoffer.ai.ragent.rag.core.retrieval;

/**
 * Three-stage budget of the retrieval funnel
 * <p>
 * A {@code retrieve → fuse → rerank → render} path has three budgets that differ in direction and cost and must stay independent.
 * They used to share one {@code topK} (further coupled to the max of intent-node topK values), so tuning one moved the other two.
 * They are split into three stages, computed once, and each stage reads only its own stage, so one int no longer has three meanings:
 * <ul>
 *   <li>{@code recallBudget}   — per-channel fan-out base (keep it large for recall; each channel multiplies by its own factor)</li>
 *   <li>{@code candidateLimit} — cap on the candidate pool sent to rerank after fusion (cost ceiling)</li>
 *   <li>{@code contextTopK}    — final count sent to the LLM (keep it small and precise; this is the product meaning of topK)</li>
 * </ul>
 * The monotone-narrowing invariant {@code recallBudget ≥ contextTopK} and {@code candidateLimit ≥ contextTopK}
 * is enforced by startup validation on the config side (see {@code SearchChannelProperties})
 */
public record RetrievalBudget(int recallBudget, int candidateLimit, int contextTopK) {

    /**
     * Constructor with all three stages equal: tests, or trivial cases that do not need distinct budgets
     */
    public static RetrievalBudget uniform(int k) {
        return new RetrievalBudget(k, k, k);
    }
}
