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

package com.nageoffer.ai.ragent.rag.core.rewrite;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;

import java.util.List;

/**
 * User query rewrite: turn a natural-language question into a query suitable for RAG retrieval
 */
public interface QueryRewriteService {

    /**
     * Rewrite the user question into a short query suitable for vector / keyword retrieval
     *
     * @param userQuestion original user question
     * @return rewritten retrieval query (falls back to the original question if rewrite fails)
     */
    String rewrite(String userQuestion);

    /**
     * Optional: rewrite and split multi-questions.
     * The default returns only the rewrite and treats it as a single sub-question
     */
    default RewriteResult rewriteWithSplit(String userQuestion) {
        String rewritten = rewrite(userQuestion);
        return new RewriteResult(rewritten, List.of(rewritten));
    }

    /**
     * Optional: rewrite and split multi-questions, with conversation history.
     * The default ignores history and falls back to the basic rewrite
     */
    default RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history) {
        return rewriteWithSplit(userQuestion);
    }
}
