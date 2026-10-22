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

import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalBudget;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieval context
 * <p>
 * Carries everything a search needs and is passed across channels
 */
@Data
@Builder
public class SearchContext {

    /**
     * Original question
     */
    private String originalQuestion;

    /**
     * Rewritten question
     */
    private String rewrittenQuestion;

    /**
     * Sub-question list
     */
    private List<String> subQuestions;

    /**
     * Intent recognition result
     */
    private List<SubQuestionIntent> intents;

    /**
     * Retrieval budget: recall fan-out, rerank candidate-pool cap, and final count, each independent.
     * Each stage reads only its own stage, so one topK does not carry several meanings
     */
    private RetrievalBudget budget;

    /**
     * Retrieval scope: directed hit collections or all collections. Computed once at the request entry and shared by every channel
     */
    private RetrievalScope retrievalScope;

    /**
     * Extra metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Primary question (prefer the rewritten question)
     */
    public String getMainQuestion() {
        return rewrittenQuestion != null ? rewrittenQuestion : originalQuestion;
    }
}
