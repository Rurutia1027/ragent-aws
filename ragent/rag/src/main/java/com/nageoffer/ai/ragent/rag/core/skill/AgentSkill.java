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

package com.nageoffer.ai.ragent.rag.core.skill;

import java.util.List;

/**
 * Runtime skill read by the Agent. Audit fields that only the console cares about are omitted.
 *
 * @param skillCode   skill id; the model loads the body by this name
 * @param name        display name
 * @param description when to use it; shipped with the catalog so the model can decide whether to load it
 * @param content     skill body Markdown
 * @param toolIds     MCP tool ids unlocked only after this skill is loaded; hidden from the model until then. The body may still name other tools.
 */
public record AgentSkill(
        String skillCode,
        String name,
        String description,
        String content,
        List<String> toolIds) {

    public AgentSkill {
        toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
    }
}
