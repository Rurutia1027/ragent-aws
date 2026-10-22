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

/**
 * Vector-space metadata and index administration (decoupled from retrieval).
 * Ensures the space exists: create it from the spec when missing; when present, check compatibility
 */
public interface VectorStoreAdmin {

    /**
     * Idempotent: ensure the vector space exists (create it when missing)
     *
     * @param spec vector-space spec (one definition across engines)
     */
    void ensureVectorSpace(VectorSpaceSpec spec);

    /**
     * Existence check only (does not create)
     */
    boolean vectorSpaceExists(VectorSpaceId spaceId);

    /**
     * Idempotent: destroy the vector space (the counterpart of {@link #ensureVectorSpace})
     * <p>
     * - Milvus: drop the collection for that knowledge base (skip when it does not exist)
     * - PG: delete leftover vector rows of that collection in the shared table (do not touch the shared HNSW index)
     *
     * @param collectionName knowledge-base collection name
     */
    void dropVectorSpace(String collectionName);
}
