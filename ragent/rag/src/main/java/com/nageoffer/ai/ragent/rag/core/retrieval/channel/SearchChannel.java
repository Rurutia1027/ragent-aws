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

import java.util.List;

/**
 * Retrieval channel interface
 * <p>
 * Each channel owns one retrieval strategy, for example:
 * - global vector search
 * - intent-directed search
 * - ES keyword search
 * <p>
 * Several channels may run in parallel and merge their results at the end
 */
public interface SearchChannel {

    /**
     * Channel name (logs and metrics)
     */
    String getName();

    /**
     * Whether this channel is enabled
     *
     * @param context retrieval context
     * @return true to enable, false to skip
     */
    boolean isEnabled(SearchContext context);

    /**
     * Run retrieval
     *
     * @param context retrieval context
     * @return retrieval result
     */
    SearchChannelResult search(SearchContext context);

    /**
     * Channel type
     */
    SearchChannelType getType();

    /**
     * Empty-result handoff: the degraded form when retrieval fails or returns nothing. Carries only channel identity and elapsed time.
     * The engine's timeout degradation and each channel's exception fallback share it, so empty results have one shape everywhere
     */
    default SearchChannelResult emptyResult(long latencyMs) {
        return SearchChannelResult.builder()
                .channelType(getType())
                .channelName(getName())
                .chunks(List.of())
                .latencyMs(latencyMs)
                .build();
    }
}
