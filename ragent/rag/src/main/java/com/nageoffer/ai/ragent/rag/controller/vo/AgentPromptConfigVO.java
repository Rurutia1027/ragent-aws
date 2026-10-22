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

package com.nageoffer.ai.ragent.rag.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Slot configuration for one agent. Slot metadata and applicability are sent together, so the frontend does not keep a second mapping table
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentPromptConfigVO {

    private String agentId;

    private String agentName;

    private Boolean builtin;

    /**
     * Name of the built-in agent, so the console can say who is used after the slot is cleared
     */
    private String defaultAgentName;

    /**
     * Current execution architecture, from ragent.engine.type, for display only
     */
    private String mode;

    private List<Slot> slots;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Slot {

        private String slotKey;
        private String displayName;

        /**
         * Writing hint above the editor. Null for an ordinary prompt that needs no extra explanation
         */
        private String editorHint;

        /**
         * Console section: WORKFLOW / AGENT / COMMON
         */
        private String group;

        private String groupName;

        /**
         * Whether this slot applies under the current architecture
         */
        private Boolean effective;

        /**
         * Why it does not apply. Null when it does apply
         */
        private String inactiveReason;

        private Set<String> requiredPlaceholders;

        /**
         * Content configured on this agent. Blank means unset and falls back to the built-in agent
         */
        private String content;
    }
}
