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

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus client configuration
 *
 * <p>
 * Creates and manages a {@link MilvusClientV2} in the Spring container for vector CRUD, index management, and related operations<br>
 * Connection address and an optional access token come from configuration
 * </p>
 *
 * <pre>
 * Example:
 *
 * milvus:
 *   uri: http://localhost:19530
 *   token: your-token-if-needed  # optional; omit or leave blank when auth is off
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusConfig {

    /**
     * Build the Milvus client bean
     *
     * <p>
     * {@code @Bean(destroyMethod = "close")} calls {@link MilvusClientV2#close()} when the Spring container shuts down,
     * releasing the connection
     * </p>
     *
     * @param uri   Milvus address, for example {@code http://localhost:19530} or a gRPC address
     * @param token Milvus auth token; may be empty. Empty means token auth is off
     * @return configured {@link MilvusClientV2} client
     */
    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(@Value("${milvus.uri}") String uri,
                                       @Value("${milvus.token:}") String token) {

        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(uri)
                .connectTimeoutMs(10000)
                .rpcDeadlineMs(30000);

        // Enable token auth when a token is configured
        if (token != null && !token.isEmpty()) {
            builder.token(token);
        }

        // Create and return the Milvus client
        return new MilvusClientV2(builder.build());
    }
}
