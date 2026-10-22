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

package com.nageoffer.ai.ragent.core.chunk.model;

/**
 * Chunk product: immutable, and it does not contain a vector
 * <p>
 * Construction requires {@code embeddingText} to be non-empty. Forgetting to inject outline context makes the object unconstructable
 *
 * @param index         index of the chunk in the document, starting at 0
 * @param content       document as written (markdown; headings stay in the body where they appeared), for LLM context and UI preview
 * @param embeddingText embedding text (outline path + body); not shown
 */
public record Chunk(
        String chunkId,
        int index,
        String content,
        String embeddingText,
        ChunkMetadata metadata
) {

    public Chunk {
        if (chunkId == null || chunkId.isBlank()) {
            throw new IllegalArgumentException("chunkId cannot be empty");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0, actual " + index);
        }
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null, chunkId=" + chunkId);
        }
        if (embeddingText == null || embeddingText.isBlank()) {
            throw new IllegalArgumentException("embeddingText cannot be empty, chunkId=" + chunkId
                    + ". Embedding text is assembled by the chunker base class with section context");
        }
        metadata = metadata == null ? ChunkMetadata.empty() : metadata;
    }

    /**
     * Copies and replaces metadata. Chunk enrichments (summary / keywords) write the extension slot.
     * Embedding text is unchanged; the vector was already computed
     */
    public Chunk withMetadata(ChunkMetadata newMetadata) {
        return new Chunk(chunkId, index, content, embeddingText, newMetadata);
    }
}
