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

package com.nageoffer.ai.ragent.rag.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.controller.request.AgentSkillPageRequest;
import com.nageoffer.ai.ragent.rag.controller.request.AgentSkillSaveRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.AgentSkillToolOptionVO;
import com.nageoffer.ai.ragent.rag.controller.vo.AgentSkillVO;
import com.nageoffer.ai.ragent.rag.service.AgentSkillAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent skill admin controller
 */
@RestController
@RequiredArgsConstructor
public class AgentSkillController {

    private final AgentSkillAdminService agentSkillAdminService;

    /**
     * Page through skills. The body is not returned
     */
    @GetMapping("/agent-skills")
    public Result<IPage<AgentSkillVO>> page(AgentSkillPageRequest requestParam) {
        return Results.success(agentSkillAdminService.pageQuery(requestParam));
    }

    /**
     * Tool options that can be referenced, from enabled MCP nodes on the intent tree
     */
    @GetMapping("/agent-skills/tool-options")
    public Result<List<AgentSkillToolOptionVO>> toolOptions() {
        return Results.success(agentSkillAdminService.listToolOptions());
    }

    /**
     * Get a skill, including its body
     */
    @GetMapping("/agent-skills/{id}")
    public Result<AgentSkillVO> detail(@PathVariable String id) {
        return Results.success(agentSkillAdminService.queryById(id));
    }

    /**
     * Create a skill
     */
    @PostMapping("/agent-skills")
    public Result<String> create(@RequestBody AgentSkillSaveRequest requestParam) {
        return Results.success(agentSkillAdminService.create(requestParam));
    }

    /**
     * Update a skill. The skill id cannot be changed
     */
    @PutMapping("/agent-skills/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody AgentSkillSaveRequest requestParam) {
        agentSkillAdminService.update(id, requestParam);
        return Results.success();
    }

    /**
     * Delete a skill. Tools it unlocked become resident again if no other enabled skill still grants them
     */
    @DeleteMapping("/agent-skills/{id}")
    public Result<Void> delete(@PathVariable String id) {
        agentSkillAdminService.delete(id);
        return Results.success();
    }

    /**
     * Enable or disable a skill
     */
    @PostMapping("/agent-skills/{id}/enabled")
    public Result<Void> toggleEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        agentSkillAdminService.toggleEnabled(id, enabled);
        return Results.success();
    }
}
