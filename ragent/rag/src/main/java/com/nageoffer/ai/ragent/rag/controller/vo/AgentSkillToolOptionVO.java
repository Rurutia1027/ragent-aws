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

/**
 * Tool options a skill may reference, from enabled MCP nodes on the intent tree
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentSkillToolOptionVO {

    private String toolId;

    /**
     * Display name configured on the intent node
     */
    private String name;

    private String description;

    /**
     * Which skill claimed this tool as an unlock. Empty when none has
     * Several skills may share one tool; only the first is reported, enough to show that a skill already owns it
     */
    private String referencedBySkillName;
}
