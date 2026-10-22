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

package com.nageoffer.ai.ragent.rag.core.retrieval.channel;

/**
 * Retrieval channel type
 */
public enum SearchChannelType {

    /**
     * Vector search.
     * One vector-modality channel. Scope is decided inside the channel from KB-intent confidence:
     * with a confident enough KB intent, narrow to the hit collections (intent-directed); otherwise fall back to all collections (global)
     */
    VECTOR,

    /**
     * Keyword search.
     * Tokenized keyword search on a full-text engine (for example Elasticsearch). The backend is an implementation detail
     */
    KEYWORD,

    /**
     * Knowledge-graph search.
     * Graph recall based on entities and relations (reserved, not implemented yet)
     */
    GRAPH,

    /**
     * Web search.
     * Live web recall from an external web search API (for example You.com Search), complementary to local knowledge-base channels
     */
    WEB_SEARCH,

    /**
     * Hybrid search.
     * Combines several retrieval strategies
     */
    HYBRID
}
