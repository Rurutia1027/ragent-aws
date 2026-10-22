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

package com.nageoffer.ai.ragent.infra.embedding;

import java.util.List;

/**
 * Embedding service
 * <p>
 * Purpose:
 * - Turns text into vectors, a core building block of the RAG system
 * - Wraps calls to the underlying embedding model (Ollama, DeepSeek, Qwen, a local inference service, and others)
 * - Exposes one vector API and hides model differences
 * <p>
 * Use cases:
 * - Embed document chunks and write them to the vector store (Indexing)
 * - Embed a query to retrieve related chunks (Retrieval)
 * <p>
 * Notes:
 * - Implementations must keep the vector dimension fixed (dimension() is constant)
 * - Batch embedding should be optimized at the model, for example fewer RPC or local-inference calls
 * - Text should be cleaned before embedding (trim, drop empties, strip control characters, and so on)
 */
public interface EmbeddingService {

    /**
     * Embed a single text
     * <p>
     * Notes:
     * - Usually builds the query embedding
     * - The output is a list of floats, for example [0.123f, -0.078f, ...]
     *
     * @param text text to embed
     * @return the vector for the text (fixed length, such as 4096)
     */
    List<Float> embed(String text);

    /**
     * Embed a single text with a named model (no retry or fallback)
     *
     * @param text    text to embed
     * @param modelId model id to use
     * @return the vector for the text
     */
    List<Float> embed(String text, String modelId);

    /**
     * Embed several texts in one batch
     * <p>
     * Notes:
     * - Usually builds a document index (Indexing), and is faster than calling embed() once per text
     * - The result order matches the input texts
     * - Implementations may use the model's batch compute to raise throughput
     *
     * @param texts text list
     * @return vector list, one vector per input text
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * Embed several texts with a named model (no retry or fallback)
     *
     * @param texts   text list
     * @param modelId model id to use
     * @return vector list
     */
    List<List<Float>> embedBatch(List<String> texts, String modelId);

    /**
     * Return the embedding dimension
     * <p>
     * Notes:
     * - Decided by the underlying model (Qwen3-Embedding is 4096, for example)
     * - Used to check vector length and the vector-store schema
     *
     * @return vector dimension (4096, 768, and so on)
     */
    default int dimension() {
        return 0;
    }
}
