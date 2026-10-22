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

package com.nageoffer.ai.ragent.rag.core.vector;

import com.nageoffer.ai.ragent.rag.core.retrieval.RetrieveRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * Vector retrieval service (VectorRetrieverService)
 * <p>
 * Purpose:
 * - wraps search against a vector database (Milvus / pgVector / Elasticsearch KNN)
 * - finds the document chunks most relevant to the user question (query)
 * - the core of the Retrieval stage in a RAG system
 * <p>
 * Flow:
 * 1. Obtain the query embedding (usually from EmbeddingService)
 * 2. Run a similarity search in the vector store
 * 3. Return the related chunks in rank order (RAGHit)
 * <p>
 * Traits:
 * - retrieval can be decoupled from the LLM call, so the search implementation can be replaced
 * - can be extended with other recall strategies: vector search, hybrid search, symbolic search, multimodal search
 * <p>
 * Notes:
 * - topK should not be large; 3 to 8 is usually the best range
 * - validate the vector dimension so it matches the vector-store schema
 */
public interface VectorRetrieverService {

    /**
     * Retrieve from a natural-language query
     * <p>
     * Notes:
     * - usually calls EmbeddingService.embed(query) first to obtain the vector
     * - then runs a similarity search in the vector store
     * - returns hit document chunks, already sorted by similarity descending
     * <p>
     * Example:
     * retrieve("Describe the onboarding process", 3)
     *
     * @param query user question in natural language
     * @param topK  number of hits to return
     * @return RetrievedChunk list (chunk content, score, metadata, and so on)
     */
    default List<RetrievedChunk> retrieve(String query, int topK) {
        RetrieveRequest req = RetrieveRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        return retrieve(req);
    }

    /**
     * Retrieve from a natural-language query, with extended parameters
     * <p>
     * Notes:
     * - usually calls EmbeddingService.embed(query) first to obtain the vector
     * - then runs a similarity search in the vector store
     * - returns hit document chunks, already sorted by similarity descending
     * <p>
     *
     * @param retrieveParam vector retrieval request
     * @return RetrievedChunk list (chunk content, score, metadata, and so on)
     */
    List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam);

    /**
     * Retrieve directly from a vector (optional)
     * <p>
     * Notes:
     * - for the case where the query embedding was already computed
     * - avoids calling the embedding model again
     * - often used to reuse an embedding across turns, or for batch retrieval
     * <p>
     * Note:
     * - the caller must keep the vector dimension consistent with the vector-store schema
     * - the implementation must not modify the query vector passed in
     *
     * @param vector        query vector (for example float[4096])
     * @param retrieveParam vector retrieval request
     * @return RetrievedChunk list (sorted by similarity)
     */
    List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam);

    /**
     * Build and normalize the query vector from a natural-language query
     *
     * @param query user question in natural language
     * @return the vector actually sent to the vector database
     */
    float[] embedAndNormalize(String query);

    /**
     * Whether one query can filter across several collections
     * <p>
     * - true: the caller uses one {@link #retrieveByVector} with the total budget for cross-collection recall (PG filters a single table by column / Milvus filters a shared store by a scalar)
     * - false: the caller falls back to per-collection parallel fan-out, takes the total budget from each collection, then truncates once
     * Both branches keep the meaning "budget is the total", so a new backend that does not override this method only changes fetch efficiency, not the recall definition
     *
     * @return whether a single cross-collection search is supported
     */
    default boolean supportsGlobalRetrieval() {
        return false;
    }
}

