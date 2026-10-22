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

package com.nageoffer.ai.ragent.infra.rerank;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * Rerank service: rerank a batch of candidates from vector retrieval
 * by relevance to the query, and return only the top topN
 */
public interface RerankService {

    /**
     * Rerank a batch of candidates from vector retrieval by relevance to the query, and return only the top topN
     *
     * @param query      user question
     * @param candidates candidates from vector retrieval (usually 3 to 5 times topK)
     * @param topN       how many to keep (the K fed to the model)
     * @return the top topN documents after rerank
     */
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN);
}
