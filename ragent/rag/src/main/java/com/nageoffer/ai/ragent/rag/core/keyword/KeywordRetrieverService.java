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

package com.nageoffer.ai.ragent.rag.core.keyword;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * Keyword retrieval SPI.
 * <p>
 * Symmetric with vector retrieval {@link com.nageoffer.ai.ragent.rag.core.vector.VectorRetrieverService}.
 * Full-text keyword search over analyzed tokens. The current implementation is Elasticsearch (BM25).
 * <p>
 * The implementation is selected by rag.keyword.type. When it is none (the default), no implementation is registered,
 * the keyword search channel is not registered either, and the system falls back to vector-only retrieval.
 * <p>
 * The return type reuses {@link RetrievedChunk}, so it is isomorphic with vector results at the channel exit and the fusion layer does not need to tell the sources apart.
 */
public interface KeywordRetrieverService {

    /**
     * Keyword search inside the shared index, filtered by collection.
     *
     * @param query           user question (already rewritten)
     * @param collectionNames target knowledge-base collections (collection_name filter); empty means no collection limit (global)
     * @param topK            recall count
     * @return hit chunks, ordered by relevance (BM25) descending, with id aligned to the vector-store primary key chunkId
     */
    List<RetrievedChunk> search(String query, List<String> collectionNames, int topK);
}
