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
 * Keyword retrieval configuration
 * <p>
 * When type=none (the default), no keyword read/write implementation is registered, which is equivalent at runtime to never having introduced keyword retrieval
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.keyword")
public class KeywordProperties {

    /**
     * Keyword retrieval backend
     * none (off) or es. none registers no keyword read/write implementation
     */
    private String type = "none";

    /**
     * Elasticsearch configuration
     */
    private Es es = new Es();

    /**
     * Physical index name shared by every knowledge base
     * <p>
     * Same shape as the shared Milvus collection and the shared PG table: one index holds every knowledge base, distinguished by collection_name
     */
    public String sharedIndex() {
        return es.getIndex();
    }

    @Data
    public static class Es {

        /**
         * ES connection address
         */
        private String uris = "http://127.0.0.1:9200";

        /**
         * Shared index name: keyword data for every knowledge base is written here and filtered by collection_name
         */
        private String index = "rag_keyword_store";

        /**
         * Index analyzer
         */
        private String analyzer = "ik_max_word";

        /**
         * Search analyzer
         */
        private String searchAnalyzer = "ik_smart";
    }
}
