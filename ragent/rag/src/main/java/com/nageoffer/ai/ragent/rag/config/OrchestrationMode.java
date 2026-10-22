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

import cn.hutool.core.util.StrUtil;

/**
 * Execution-architecture tier, set by ragent.engine.type
 * <p>
 * A deployment-level choice (switching requires a restart, and AGENT depends on an external ReAct service), so it is not switchable from the admin UI
 */
public enum OrchestrationMode {

    /**
     * v1 orchestration pipeline: intent classification, then retrieval, then synthesis. The path is fixed and latency is low
     */
    WORKFLOW,

    /**
     * v2 ReAct architecture: the main Agent decides, and the RAG pipeline is demoted to one Tool
     */
    AGENT;

    /**
     * Parse the configured value, case-insensitive. Unrecognized values fall back to WORKFLOW
     */
    public static OrchestrationMode of(String value) {
        if (StrUtil.isBlank(value)) {
            return WORKFLOW;
        }
        for (OrchestrationMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return WORKFLOW;
    }
}
