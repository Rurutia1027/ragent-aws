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

package com.nageoffer.ai.ragent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG system defaults
 *
 * <p>
 * Default vector-database settings: collection name, dimension, and metric type
 * </p>
 *
 * <pre>
 * Example:
 *
 * rag:
 *   default:
 *     collection-name: default_collection
 *     dimension: 768
 *     metric-type: COSINE
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.default")
public class RAGDefaultProperties {

    /**
     * Default vector collection name
     * <p>
     * Name of the default collection that stores vectors
     */
    private String collectionName;

    /**
     * Vector dimension
     * <p>
     * Must match the embedding model's output dimension
     * Examples: 2048, 4096
     */
    private Integer dimension;

    /**
     * Vector similarity metric
     * <p>
     * How similarity between vectors is measured. Common values:
     * <ul>
     *   <li>{@code COSINE}: cosine similarity</li>
     *   <li>{@code L2}: Euclidean distance</li>
     *   <li>{@code IP}: inner product</li>
     * </ul>
     */
    private String metricType;

    /**
     * Global SSE timeout (milliseconds)
     * <p>
     * Safety net against leaked SSE connections. The connection is closed on timeout. Default is 5 minutes
     */
    private Long sseTimeoutMs = 5 * 60 * 1000L;
}
