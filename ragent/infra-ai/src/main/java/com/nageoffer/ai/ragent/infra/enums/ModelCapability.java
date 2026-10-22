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

package com.nageoffer.ai.ragent.infra.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Model capability enum
 * Capability types an AI model can support
 */
@Getter
@RequiredArgsConstructor
public enum ModelCapability {

    /**
     * Chat
     * Natural-language conversation with the user
     */
    CHAT("Chat"),

    /**
     * Embedding
     * Converts text to vectors for semantic search and similarity
     */
    EMBEDDING("Embedding"),

    /**
     * Rerank
     * Reorders search results to improve relevance
     */
    RERANK("Rerank");

    /**
     * Display name of the capability
     */
    private final String displayName;
}
