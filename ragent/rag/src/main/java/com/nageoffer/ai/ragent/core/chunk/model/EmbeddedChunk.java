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
 * A chunk that already has a vector: the only input the index layer accepts
 * <p>
 * Kept separate from {@link Chunk} so a chunk without a vector cannot enter the index layer by type. A record's
 * equals / hashCode compares arrays by reference. This type is only a data carrier; do not put it in a Set or use it as a Map key
 *
 * @param embedding vector. Dimension is fixed by deployment config and checked during embedding, before write
 */
public record EmbeddedChunk(Chunk chunk, float[] embedding) {

    public EmbeddedChunk {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk cannot be null");
        }
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding cannot be empty, chunkId=" + chunk.chunkId());
        }
    }

    public String chunkId() {
        return chunk.chunkId();
    }

    public int index() {
        return chunk.index();
    }

    public String content() {
        return chunk.content();
    }

    public String embeddingText() {
        return chunk.embeddingText();
    }

    public ChunkMetadata metadata() {
        return chunk.metadata();
    }

    public int dimension() {
        return embedding.length;
    }
}
