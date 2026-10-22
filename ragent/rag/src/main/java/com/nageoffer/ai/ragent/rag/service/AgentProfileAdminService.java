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

package com.nageoffer.ai.ragent.rag.service;

import com.nageoffer.ai.ragent.rag.controller.request.AgentProfileSaveRequest;
import com.nageoffer.ai.ragent.rag.controller.request.AgentPromptSaveRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.AgentProfileListVO;
import com.nageoffer.ai.ragent.rag.controller.vo.AgentPromptConfigVO;

public interface AgentProfileAdminService {

    /**
     * List every agent, built-in first, then the rest by creation time
     */
    AgentProfileListVO list();

    String create(AgentProfileSaveRequest requestParam);

    void update(String id, AgentProfileSaveRequest requestParam);

    void delete(String id);

    /**
     * Activate the given agent. Globally only one stays active
     */
    void activate(String id);

    /**
     * Get every slot for this agent, including metadata and whether each slot applies under the current architecture
     */
    AgentPromptConfigVO loadPrompts(String id);

    /**
     * Save one slot. Blank content restores the fallback to the built-in agent
     */
    void savePrompt(String id, String slotKey, AgentPromptSaveRequest requestParam);

    /**
     * Read the built-in agent's content for this slot, for the console "copy from default"
     */
    String defaultPrompt(String slotKey);
}
