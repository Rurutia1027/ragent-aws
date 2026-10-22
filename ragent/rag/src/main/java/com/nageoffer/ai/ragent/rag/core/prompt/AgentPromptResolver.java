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

package com.nageoffer.ai.ragent.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.nageoffer.ai.ragent.rag.dao.entity.AgentProfileDO;
import com.nageoffer.ai.ragent.rag.dao.entity.AgentPromptDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.AgentProfileMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.AgentPromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent prompt resolver.
 * Prefer the active agent's slot; a blank slot falls back to the built-in agent. Prompts shown to end users are always read from here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPromptResolver {

    private final AgentProfileMapper agentProfileMapper;
    private final AgentPromptMapper agentPromptMapper;
    private final AgentPromptCacheManager cacheManager;

    /**
     * @return the slot prompt, or an empty string when the built-in agent has none either
     */
    public String resolve(AgentPromptSlot slot) {
        if (slot == null) {
            return "";
        }
        return StrUtil.emptyIfNull(resolveAll().get(slot.name()));
    }

    /**
     * Fill placeholders and clean formatting. Same semantics as PromptTemplateLoader#render.
     */
    public String render(AgentPromptSlot slot, Map<String, String> slots) {
        return PromptTemplateUtils.cleanupPrompt(PromptTemplateUtils.fillSlots(resolve(slot), slots));
    }

    /**
     * @return the effective content of every slot; missing slots are absent from the map
     */
    public Map<String, String> resolveAll() {
        Map<String, String> cached = cacheManager.getFromCache();
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        Map<String, String> resolved = loadFromDb();
        cacheManager.saveToCache(resolved);
        return resolved;
    }

    /**
     * Read the slots configured on one agent, with no fallback, for the console edit view.
     */
    public Map<String, String> loadOwnPrompts(String agentId) {
        Map<String, String> own = new HashMap<>();
        if (StrUtil.isBlank(agentId)) {
            return own;
        }
        List<AgentPromptDO> prompts = agentPromptMapper.selectList(
                Wrappers.lambdaQuery(AgentPromptDO.class).eq(AgentPromptDO::getAgentId, agentId));
        for (AgentPromptDO prompt : prompts) {
            own.put(prompt.getSlotKey(), StrUtil.emptyIfNull(prompt.getContent()));
        }
        return own;
    }

    private Map<String, String> loadFromDb() {
        AgentProfileDO builtin = firstByFlag(AgentProfileDO::getBuiltin);
        if (builtin == null) {
            log.warn("未找到内置智能体，空槽位将无提示词可回落");
        }

        // Lay the built-in agent down as the baseline, then let the active agent's non-blank slots override it. Overwriting the same row twice is harmless.
        Map<String, String> resolved = new HashMap<>();
        putNonBlank(resolved, builtin);
        putNonBlank(resolved, firstByFlag(AgentProfileDO::getActive));
        return resolved;
    }

    private AgentProfileDO firstByFlag(SFunction<AgentProfileDO, Integer> flag) {
        List<AgentProfileDO> profiles = agentProfileMapper.selectList(
                Wrappers.lambdaQuery(AgentProfileDO.class)
                        .eq(flag, 1)
                        .orderByAsc(AgentProfileDO::getCreateTime)
                        .orderByAsc(AgentProfileDO::getId));
        return CollUtil.isEmpty(profiles) ? null : profiles.get(0);
    }

    /**
     * A later write overrides an earlier one. Blank content does not override, which is how fallback works.
     */
    private void putNonBlank(Map<String, String> target, AgentProfileDO profile) {
        if (profile == null) {
            return;
        }
        loadOwnPrompts(profile.getId()).forEach((slotKey, content) -> {
            if (StrUtil.isNotBlank(content)) {
                target.put(slotKey, content);
            }
        });
    }
}
