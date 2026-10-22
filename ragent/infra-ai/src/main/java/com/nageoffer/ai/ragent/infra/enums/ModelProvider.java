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
 * Model provider enum
 * Centralizes provider names so string constants are not scattered
 */
@Getter
@RequiredArgsConstructor
public enum ModelProvider {

    /**
     * Ollama local model service
     */
    OLLAMA("ollama"),

    /**
     * Alibaba Cloud Bailian model platform
     */
    BAI_LIAN("bailian"),

    /**
     * SiliconFlow model service
     */
    SILICON_FLOW("siliconflow"),

    /**
     * AIHubMix model service
     */
    AI_HUB_MIX("aihubmix"),

    /**
     * Official DeepSeek platform
     */
    DEEP_SEEK("deepseek"),

    /**
     * No-op implementation for tests or placeholders
     */
    NOOP("noop");

    private final String id;

    public boolean matches(String provider) {
        return provider != null && provider.equalsIgnoreCase(id);
    }
}
