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

import com.nageoffer.ai.ragent.rag.config.OrchestrationMode;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Agent prompt slots, the single source of truth for slot metadata.
 * <p>
 * Slots are named by function, not by architecture: the scope of a slot changes as v1/v2 evolve, so it is not encoded in the identifier.
 */
@Getter
public enum AgentPromptSlot {

    SYSTEM_CHAT("闲聊应答", Group.WORKFLOW,
            Set.of(OrchestrationMode.WORKFLOW),
            "Agent 模式下由主 Agent 直接应答",
            Set.of()),

    AGENT_MAIN("Agent人设", Group.AGENT,
            Set.of(OrchestrationMode.AGENT),
            "WorkFlow 模式不经过 ReAct 架构",
            Set.of()),

    /**
     * The only slot that is not a dialogue message: it is shipped with the tool definition, and the model must understand it before the call.
     */
    KNOWLEDGE_TOOL_DESCRIPTION("知识库工具声明", Group.AGENT,
            Set.of(OrchestrationMode.AGENT),
            "WorkFlow 模式不注册原生知识库工具",
            Set.of(),
            "模型靠它判断要不要查知识库，此时还看不到检索结果；写清这个库覆盖哪类问题，不必在这里规定回答风格"),

    AGENT_CONTEXT_COMPACTION("Agent上下文压缩", Group.AGENT,
            Set.of(OrchestrationMode.AGENT),
            "WorkFlow 模式不做上下文压缩，长会话走「历史对话摘要」",
            Set.of("{summary_max_chars}"),
            "产物会以历史消息的身份回填进后续每一轮，而被它替代的原文届时已经删除；"
                    + "要求写清调用过哪些工具、得到什么结论、还剩什么没做，不必在这里规定回答风格"),

    AGENT_MEMORY_EXTRACTION("长期记忆抽取", Group.AGENT,
            Set.of(OrchestrationMode.AGENT),
            "WorkFlow 模式不沉淀跨会话事实",
            Set.of("{existing_memories}", "{recent_turns}", "{memory_max_chars}"),
            "产物不进对话，由代码按 JSON 数组解析后直接写库，解析失败整批作废；"
                    + "写清什么该记、什么不该记、怎么指认已有条目、什么算要求清空全部，不要在这里规定回答风格"),

    AGENT_MEMORY_CONSOLIDATION("长期记忆受限合并", Group.AGENT,
            Set.of(OrchestrationMode.AGENT),
            "WorkFlow 模式不沉淀跨会话事实",
            Set.of("{existing_memories}", "{target_chars}"),
            "仅当记忆总量顶到上限时才调用一次；产物不进对话，由代码按 JSON 数组解析后整组替换旧条目；"
                    + "写清什么算同一件事，以及绝不许为了压体量删掉一条独立记忆"),

    /**
     * Same as KNOWLEDGE_TOOL_DESCRIPTION: shipped with the tool definition, and the model must understand it before the call.
     */
    AGENT_MEMORY_TOOL_DESCRIPTION("记忆整理工具声明", Group.AGENT,
            Set.of(OrchestrationMode.AGENT),
            "WorkFlow 模式不注册记忆整理工具",
            Set.of(),
            "模型靠它判断这轮要不要整理记忆，此时还看不到整理结果；"
                    + "「记住」「忘掉」「清空全部」三类场景都要写到，具体记什么忘什么由抽取环节判断"),

    /**
     * Shared by both architectures: composed on the main path under WorkFlow, and inside the RAG tool under Agent.
     */
    KB_ANSWER("知识库应答", Group.COMMON,
            Set.of(OrchestrationMode.WORKFLOW, OrchestrationMode.AGENT),
            null,
            Set.of()),

    /**
     * Must not be merged with "Agent context compaction": this one is a topic index without conclusions, and that one must keep the conclusions.
     */
    CONVERSATION_SUMMARY("历史对话摘要", Group.WORKFLOW,
            Set.of(OrchestrationMode.WORKFLOW),
            "Agent 模式的长会话改由「Agent 上下文压缩」承接",
            Set.of("{summary_max_chars}")),

    RECOMMENDED_QUESTIONS("追问推荐", Group.COMMON,
            Set.of(OrchestrationMode.WORKFLOW, OrchestrationMode.AGENT),
            null,
            Set.of("{chunks}", "{count}", "{question}", "{answer}"));

    private final String displayName;

    private final Group group;

    private final Set<OrchestrationMode> effectiveModes;

    /**
     * Reason shown to the admin when the slot is inactive. Null for slots that apply to both architectures.
     */
    private final String inactiveReason;

    /**
     * Placeholders that must appear. A missing one makes a downstream rule fail silently, so save rejects it.
     */
    private final Set<String> requiredPlaceholders;

    /**
     * Writing hint shown above the editor, only when this text is used differently from a normal prompt. Null for the other slots.
     */
    private final String editorHint;

    AgentPromptSlot(String displayName, Group group, Set<OrchestrationMode> effectiveModes,
                    String inactiveReason, Set<String> requiredPlaceholders) {
        this(displayName, group, effectiveModes, inactiveReason, requiredPlaceholders, null);
    }

    AgentPromptSlot(String displayName, Group group, Set<OrchestrationMode> effectiveModes,
                    String inactiveReason, Set<String> requiredPlaceholders, String editorHint) {
        this.displayName = displayName;
        this.group = group;
        this.effectiveModes = effectiveModes;
        this.inactiveReason = inactiveReason;
        this.requiredPlaceholders = requiredPlaceholders;
        this.editorHint = editorHint;
    }

    public boolean isEffectiveIn(OrchestrationMode mode) {
        return effectiveModes.contains(mode);
    }

    /**
     * Slots that are actually read under the current architecture. The console uses this as the coverage denominator.
     */
    public static List<AgentPromptSlot> effectiveIn(OrchestrationMode mode) {
        return Arrays.stream(values())
                .filter(slot -> slot.isEffectiveIn(mode))
                .toList();
    }

    public static Optional<AgentPromptSlot> find(String key) {
        return Arrays.stream(values())
                .filter(slot -> slot.name().equalsIgnoreCase(key))
                .findFirst();
    }

    /**
     * Console grouping, split by where the slot applies rather than by historical ownership.
     */
    @Getter
    public enum Group {

        WORKFLOW("WorkFlow专属"),
        AGENT("Agent专属"),
        COMMON("通用");

        private final String displayName;

        Group(String displayName) {
            this.displayName = displayName;
        }
    }
}
