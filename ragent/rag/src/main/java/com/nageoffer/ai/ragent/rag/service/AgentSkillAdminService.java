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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.rag.controller.request.AgentSkillPageRequest;
import com.nageoffer.ai.ragent.rag.controller.request.AgentSkillSaveRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.AgentSkillToolOptionVO;
import com.nageoffer.ai.ragent.rag.controller.vo.AgentSkillVO;

import java.util.List;

/**
 * Skill admin service
 */
public interface AgentSkillAdminService {

    /**
     * Page through skills. The body is not returned
     */
    IPage<AgentSkillVO> pageQuery(AgentSkillPageRequest requestParam);

    /**
     * Get a skill, including its body
     */
    AgentSkillVO queryById(String id);

    /**
     * Create a skill
     *
     * @return the new skill ID
     */
    String create(AgentSkillSaveRequest requestParam);

    /**
     * Update a skill. The skill id cannot be changed
     */
    void update(String id, AgentSkillSaveRequest requestParam);

    /**
     * Delete a skill. Tools it unlocked become resident again if no other enabled skill still grants them
     */
    void delete(String id);

    /**
     * Enable or disable a skill
     */
    void toggleEnabled(String id, boolean enabled);

    /**
     * Tool options that can be referenced, from enabled MCP nodes on the intent tree
     */
    List<AgentSkillToolOptionVO> listToolOptions();
}
