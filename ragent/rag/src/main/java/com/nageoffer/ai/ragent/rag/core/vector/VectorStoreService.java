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

import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;

import java.util.List;

/**
 * Vector store service
 */
public interface VectorStoreService {

    /**
     * Build vector indexes for a document in batch
     *
     * @param collectionName vector-space name (knowledge-base collectionName)
     * @param docId          document id
     * @param chunks         document chunks; embeddings must already be computed
     */
    void indexDocumentChunks(String collectionName, String docId, List<EmbeddedChunk> chunks);

    /**
     * Update the vector index of one chunk
     *
     * @param collectionName vector-space name (knowledge-base collectionName)
     * @param docId          document id
     * @param chunk          chunk to update; must carry the latest embedding
     */
    void updateChunk(String collectionName, String docId, EmbeddedChunk chunk);

    /**
     * Delete every vector index of a document
     *
     * @param collectionName vector-space name (knowledge-base collectionName)
     * @param docId          document id
     */
    void deleteDocumentVectors(String collectionName, String docId);

    /**
     * Delete the vector index of one chunk
     *
     * @param collectionName vector-space name (knowledge-base collectionName)
     * @param chunkId        chunk id
     */
    void deleteChunkById(String collectionName, String chunkId);

    /**
     * Delete vector indexes of the given chunks in batch
     *
     * @param collectionName vector-space name (knowledge-base collectionName)
     * @param chunkIds       chunk ids
     */
    void deleteChunksByIds(String collectionName, List<String> chunkIds);
}
