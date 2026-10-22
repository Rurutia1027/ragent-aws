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

import com.nageoffer.ai.ragent.rag.dao.entity.AgentProfileDO;
import com.nageoffer.ai.ragent.rag.dao.entity.AgentPromptDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.AgentProfileMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.AgentPromptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the fallback chain: a non-blank slot on the active agent overrides the built-in; a blank slot falls back to the built-in
 * <p>
 * Each case calls resolveAll once, because the mapper stub returns in call order (built-in first, then active)
 */
@ExtendWith(MockitoExtension.class)
class AgentPromptResolverTest {

    private static final String BUILTIN_ID = "1";
    private static final String ACTIVE_ID = "2";

    @Mock
    private AgentProfileMapper agentProfileMapper;

    @Mock
    private AgentPromptMapper agentPromptMapper;

    @Mock
    private AgentPromptCacheManager cacheManager;

    private AgentPromptResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AgentPromptResolver(agentProfileMapper, agentPromptMapper, cacheManager);
        when(cacheManager.getFromCache()).thenReturn(null);
    }

    /**
     * The built-in fills all 7 slots. The active agent sets only KB_ANSWER. The rest must fall back, not become empty strings
     */
    @Test
    void fallsBackToBuiltinForUnsetSlots() {
        stubProfiles(profile(BUILTIN_ID, 1, 0), profile(ACTIVE_ID, 0, 1));
        stubPromptCalls(
                allSlots(BUILTIN_ID, "内置内容"),
                List.of(prompt(ACTIVE_ID, AgentPromptSlot.KB_ANSWER, "自定义知识库人设"))
        );

        Map<String, String> resolved = resolver.resolveAll();

        assertEquals("自定义知识库人设", resolved.get(AgentPromptSlot.KB_ANSWER.name()));
        for (AgentPromptSlot slot : AgentPromptSlot.values()) {
            if (slot != AgentPromptSlot.KB_ANSWER) {
                assertEquals("内置内容", resolved.get(slot.name()),
                        slot.name() + " 应回落内置而不是空串");
            }
        }
    }

    /**
     * Blank content is the same as unset: clearing the textarea and saving restores the fallback
     */
    @Test
    void treatsBlankContentAsUnset() {
        stubProfiles(profile(BUILTIN_ID, 1, 0), profile(ACTIVE_ID, 0, 1));
        stubPromptCalls(
                List.of(prompt(BUILTIN_ID, AgentPromptSlot.KB_ANSWER, "内置内容")),
                List.of(prompt(ACTIVE_ID, AgentPromptSlot.KB_ANSWER, "   \n  "))
        );

        assertEquals("内置内容", resolver.resolveAll().get(AgentPromptSlot.KB_ANSWER.name()));
    }

    /**
     * With no active agent, everything uses the built-in and must not throw
     */
    @Test
    void usesBuiltinWhenNoActiveProfile() {
        stubProfiles(profile(BUILTIN_ID, 1, 0), null);
        stubPromptCalls(List.of(prompt(BUILTIN_ID, AgentPromptSlot.SYSTEM_CHAT, "内置闲聊")));

        assertEquals("内置闲聊", resolver.resolveAll().get(AgentPromptSlot.SYSTEM_CHAT.name()));
    }

    /**
     * An empty map is an invalid cache: it must go back to the database, not be returned as a prompt snapshot that already hit
     */
    @Test
    void reloadsFromDbWhenCachedPromptsAreEmpty() {
        when(cacheManager.getFromCache()).thenReturn(Map.of());
        stubProfiles(profile(BUILTIN_ID, 1, 0), null);
        stubPromptCalls(List.of(prompt(BUILTIN_ID, AgentPromptSlot.AGENT_MAIN, "内置 Agent 人设")));

        Map<String, String> resolved = resolver.resolveAll();

        assertEquals("内置 Agent 人设", resolved.get(AgentPromptSlot.AGENT_MAIN.name()));
    }

    /**
     * When even the built-in is missing, resolve returns an empty string rather than null, so downstream does not concatenate the literal null
     */
    @Test
    void returnsEmptyStringWhenNothingConfigured() {
        stubProfiles(null, null);

        assertNull(resolver.resolveAll().get(AgentPromptSlot.KB_ANSWER.name()));
        assertEquals("", resolver.resolve(AgentPromptSlot.KB_ANSWER));
    }

    // === stub data ===

    /**
     * Built-in and active each query profile once and return in call order
     */
    private void stubProfiles(AgentProfileDO builtin, AgentProfileDO active) {
        when(agentProfileMapper.selectList(any())).thenReturn(
                builtin == null ? List.of() : List.of(builtin),
                active == null ? List.of() : List.of(active));
    }

    @SafeVarargs
    private void stubPromptCalls(List<AgentPromptDO> first, List<AgentPromptDO>... rest) {
        when(agentPromptMapper.selectList(any())).thenReturn(first, rest);
    }

    private static AgentProfileDO profile(String id, int builtin, int active) {
        return AgentProfileDO.builder()
                .id(id)
                .name("agent-" + id)
                .builtin(builtin)
                .active(active)
                .createTime(new Date())
                .build();
    }

    private static AgentPromptDO prompt(String agentId, AgentPromptSlot slot, String content) {
        return AgentPromptDO.builder()
                .id(agentId + "-" + slot.name())
                .agentId(agentId)
                .slotKey(slot.name())
                .content(content)
                .build();
    }

    private static List<AgentPromptDO> allSlots(String agentId, String content) {
        List<AgentPromptDO> prompts = new ArrayList<>();
        for (AgentPromptSlot slot : AgentPromptSlot.values()) {
            prompts.add(prompt(agentId, slot, content));
        }
        return prompts;
    }
}
