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

package com.nageoffer.ai.ragent.rag.controller.request;

import lombok.Data;

import java.util.List;

/**
 * Skill save request, shared by create and edit
 */
@Data
public class AgentSkillSaveRequest {

    /**
     * Skill id. The model loads the body by this name. Immutable after create
     */
    private String skillCode;

    /**
     * Skill display name
     */
    private String name;

    /**
     * When to use this skill. State clearly when it should be used
     */
    private String description;

    /**
     * Skill body Markdown
     */
    private String content;

    /**
     * MCP tool IDs unlocked only after this skill is loaded. Values must come from MCP nodes on the intent tree
     */
    private List<String> toolIds;

    /**
     * Sort order. Smaller values come first
     */
    private Integer sortOrder;

    /**
     * Whether enabled. Omitted means enabled
     */
    private Boolean enabled;
}
