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
import com.nageoffer.ai.ragent.infra.model.ModelTarget;

import java.util.List;

/**
 * Rerank client
 * Reorders retrieved document chunks to raise the relevance of the result
 */
public interface RerankClient {

    /**
     * Provider name of the rerank service
     *
     * @return provider id, such as "bailian" or "jina"
     */
    String provider();

    /**
     * Reorder retrieved document chunks
     *
     * @param query      user query text
     * @param candidates candidate chunks to rank
     * @param topN       how many of the most relevant results to return
     * @param target     target model settings
     * @return reranked chunks, highest relevance first
     */
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target);
}
