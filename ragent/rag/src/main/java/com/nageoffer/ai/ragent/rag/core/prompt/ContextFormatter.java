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

package com.nageoffer.ai.ragent.rag.core.prompt;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;

import java.util.List;
import java.util.Set;

/**
 * Context formatter. Turns knowledge-base retrieval results into text that can be embedded in a prompt.
 */
public interface ContextFormatter {

    /**
     * Format knowledge-base retrieval context.
     *
     * @param kbIntents         knowledge-base intent nodes and their scores
     * @param eligibleIntentIds intent ids allowed to inject answer rules
     * @param rerankedChunks    ordered chunks after post-processing
     * @param contextTopK       cap on chunks that reach the LLM (the contextTopK stage of the retrieval budget)
     * @return formatted knowledge-base context text
     */
    String formatKbContext(List<NodeScore> kbIntents,
                           Set<String> eligibleIntentIds,
                           List<RetrievedChunk> rerankedChunks,
                           int contextTopK);

}
