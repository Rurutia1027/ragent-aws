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
import org.springframework.stereotype.Component;

/**
 * Knowledge-graph retrieval configuration
 * <p>
 * When type=none (the default), no graph read/write implementation is registered (neither the retrieval channel nor the write-sync decorator is woven),
 * which is equivalent at runtime to never having introduced graph retrieval
 * <p>
 * Symmetric with keyword retrieval: this block owns backend type and connection (the counterpart of rag.keyword).
 * Channel behavior (enabled / scope / multiplier) lives on channels.graph in {@link SearchChannelProperties}
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.graph")
public class GraphProperties {

    /**
     * Graph retrieval backend
     * none (off) or lightrag. none registers no graph read/write implementation
     */
    private String type = "none";

    /**
     * LightRAG service connection
     */
    private LightRag lightrag = new LightRag();

    /**
     * Embedding model id on the graph side
     * Independent of each knowledge base's vector embedding. Cannot be changed after the first index
     */
    private String embeddingModel = "";

    /**
     * Whether the lightrag backend is enabled
     */
    public boolean isLightrag() {
        return "lightrag".equalsIgnoreCase(type);
    }

    @Data
    public static class LightRag {

        /**
         * LightRAG server base URL
         */
        private String baseUrl = "http://127.0.0.1:9621";

        /**
         * API key (LightRAG X-API-Key header)
         * Local deploys leave this blank and do not send the header. Set it only when external auth is required
         */
        private String apiKey = "";

        /**
         * LightRAG retrieval mode (naive / local / global / hybrid / mix), passed through as the mode field of /query.
         * mix also mixes in chunks from LightRAG's internal vector search: the content overlaps the vector channel but the ids differ, so RRF cannot drop the duplicates.
         * hybrid returns only evidence tied to the graph structure, orthogonal to the vector / keyword channels. naive does not use the graph and is not recommended here
         */
        private String queryMode = "hybrid";
    }
}
