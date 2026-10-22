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

package com.nageoffer.ai.ragent.rag.core.retrieval.postprocessor;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchContext;

import java.util.List;

/**
 * Post-processor interface for retrieval results
 * <p>
 * Shared post-processing of multi-channel results, for example:
 * - dedup
 * - version filtering
 * - score normalization
 * - rerank
 * <p>
 * Processors run in order and form a chain
 */
public interface SearchResultPostProcessor {

    /**
     * Processor name
     */
    String getName();

    /**
     * Processor priority (smaller numbers run first)
     */
    int getOrder();

    /**
     * Whether this processor is enabled
     *
     * @param context retrieval context
     * @return true to enable, false to skip
     */
    boolean isEnabled(SearchContext context);

    /**
     * Process retrieval results
     *
     * @param chunks  current chunk list (may be the previous processor's output)
     * @param results original multi-channel results (for metadata)
     * @param context retrieval context
     * @return processed chunk list
     */
    List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                 List<SearchChannelResult> results,
                                 SearchContext context);
}
