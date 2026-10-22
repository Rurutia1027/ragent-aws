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

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieval channel result
 * <p>
 * Wraps one channel's hits and metadata
 */
@Data
@Builder
public class SearchChannelResult {

    /**
     * Channel type
     */
    private SearchChannelType channelType;

    /**
     * Channel name
     */
    private String channelName;

    /**
     * Retrieved chunk list
     */
    private List<RetrievedChunk> chunks;

    /**
     * Retrieval elapsed time (milliseconds)
     */
    private long latencyMs;

    /**
     * Extra metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
