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

package com.nageoffer.ai.ragent.core.ingest;

/**
 * Vector-target identity: which logical partition a chunk is written to, which model, and which dimension is required.
 * Composed from knowledge-base config (L2) and deployment config (L1)
 * <p>
 * {@link #partition} is a logical partition key. It is not the physical space named by {@code rag.core.vector.VectorSpaceId}
 * (under PG, a shared table and a shared index; under Milvus, a collection). Do not call either one collectionName.
 * Model and dimension travel with the target. If either is missing, falling back to the system default is not allowed
 *
 * @param partition      logical partition key, taken from the knowledge base's collection_name
 * @param embeddingModel embedding model id, taken from knowledge-base config
 * @param dimension      vector dimension, taken from deployment config; a global hard constraint
 */
public record VectorTarget(String partition, String embeddingModel, int dimension) {

    public VectorTarget {
        if (partition == null || partition.isBlank()) {
            throw new IllegalArgumentException("partition cannot be empty");
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel cannot be empty, partition=" + partition
                    + ". The embedding model is a knowledge-base setting and cannot fall back to the system default");
        }
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be > 0, actual " + dimension);
        }
    }
}
