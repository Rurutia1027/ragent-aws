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

import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;

import java.util.List;

/**
 * Keyword index SPI, symmetric with the vector writer {@link com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService}.
 * Writes a chunk's keyword text into the full-text engine.
 * <p>
 * The document id (ES {@code _id}) must equal the vector-store primary key chunkId, or cross-modal dedup and fusion cannot align. Every knowledge base
 * writes the same physical index and is distinguished by {@code collection_name}, isomorphic with the shared vector collection. The implementation is
 * selected by {@code rag.keyword.type}. When it is none, no implementation is registered and the write-side decorator is not registered either.
 */
public interface KeywordIndexService {

    /**
     * Index keyword text for a document's chunks in batch.
     *
     * @param collectionName knowledge-base collection name (written to collection_name to distinguish collections)
     * @param docId          document id
     * @param chunks         document chunks
     */
    void indexDocumentChunks(String collectionName, String docId, List<EmbeddedChunk> chunks);

    /**
     * Update the keyword index of one chunk.
     *
     * @param collectionName knowledge-base collection name
     * @param docId          document id
     * @param chunk          chunk to update
     */
    void updateChunk(String collectionName, String docId, EmbeddedChunk chunk);

    /**
     * Delete every keyword index for a document.
     *
     * @param collectionName knowledge-base collection name
     * @param docId          document id
     */
    void deleteDocumentIndex(String collectionName, String docId);

    /**
     * Delete the keyword index of one chunk.
     *
     * @param collectionName knowledge-base collection name
     * @param chunkId        chunk id
     */
    void deleteChunkById(String collectionName, String chunkId);

    /**
     * Delete keyword indexes for the given chunks in batch.
     *
     * @param collectionName knowledge-base collection name
     * @param chunkIds       chunk ids
     */
    void deleteChunksByIds(String collectionName, List<String> chunkIds);

    /**
     * Delete all keyword data for one knowledge base from the shared index (cleanup when the knowledge base is deleted).
     *
     * @param collectionName knowledge-base collection name
     */
    void deleteByCollection(String collectionName);
}
