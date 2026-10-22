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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * RAG feature configuration
 *
 * <p>
 * Feature switches for the RAG system, such as query rewrite
 * </p>
 *
 * <pre>
 * Example:
 *
 * rag:
 *   query-rewrite:
 *     enabled: true
 * </pre>
 */
@Data
@Configuration
public class RAGConfigProperties {

    /**
     * Query-rewrite switch
     * <p>
     * Whether to rewrite the user query into a form better suited to retrieval
     * Default: {@code true}
     */
    @Value("${rag.query-rewrite.enabled:true}")
    private Boolean queryRewriteEnabled;

    /**
     * Rerank switch
     * <p>
     * Whether the Rerank post-processor reorders recalled results
     * Default: {@code true}
     */
    @Value("${rag.rerank.enabled:true}")
    private Boolean rerankEnabled;

    /**
     * Context metadata enrichment switch
     * <p>
     * Whether, at the end of retrieval, to look up each chunk's document (document ID / sequence / title),
     * then group context by document, order chunks within a group, and use the document title as an internal anchor
     * When off, assembly falls back to a flat relevance order with no sources
     * Default: {@code true}
     */
    @Value("${rag.context.enrich.enabled:true}")
    private Boolean contextEnrichEnabled;

    /**
     * Inline citation (numeric marker) switch
     * <p>
     * When on, materials entering the context get a request-scoped citation number and an inline-citation rule is appended, so the model ends passages with {@code [N](#cite-N)}
     * When off, numbers are not injected and the inline-citation rule is not appended, saving that system-prompt and context cost and lowering time to first token;
     * the document-level source panel is sent separately over SSE and is unaffected by this switch
     * Default: {@code false}
     */
    @Value("${rag.citation.enabled:false}")
    private Boolean citationEnabled;
}
