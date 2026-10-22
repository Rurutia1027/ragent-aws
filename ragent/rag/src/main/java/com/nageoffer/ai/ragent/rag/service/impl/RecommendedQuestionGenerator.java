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

package com.nageoffer.ai.ragent.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.GroundingChunk;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import com.nageoffer.ai.ragent.infra.util.LLMResponseCleaner;
import com.nageoffer.ai.ragent.rag.core.prompt.AgentPromptResolver;
import com.nageoffer.ai.ragent.rag.core.prompt.AgentPromptSlot;
import com.nageoffer.ai.ragent.rag.dto.RecommendedQuestionsPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Recommended follow-up generator
 * <p>
 * An LLM side call (FAST tier) after the answer, triggered by a separate API, off the chat streaming critical path
 * A separate bean so Spring AOP can intercept {@link RagTraceNode} (a self-call in the same class does not hit the proxy)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendedQuestionGenerator {

    private static final int DEFAULT_RECOMMEND_COUNT = 3;
    private static final int MAX_QUESTION_CHARS = 1000;
    private static final int MAX_ANSWER_CHARS = 6000;
    private static final int MAX_CHUNKS_CHARS = 6000;
    private static final int MAX_OUTPUT_TOKENS = 256;
    private static final int MAX_QUESTION_ITEM_CHARS = 200;

    private final AgentPromptResolver agentPromptResolver;
    private final LLMService llmService;

    @RagTraceNode(name = "recommended-question-gen", type = "RECOMMEND_GEN")
    public RecommendedQuestionsPayload generate(String question, String answer, List<GroundingChunk> chunks) {
        try {
            int count = DEFAULT_RECOMMEND_COUNT;
            // The only authority for the prompt input budget: question, answer, and snippets are truncated together at the model-call boundary, so upstream assembly is not limited twice and leftover data does not leak through
            String prompt = agentPromptResolver.render(
                    AgentPromptSlot.RECOMMENDED_QUESTIONS,
                    Map.of(
                            "question", StrUtil.subPre(StrUtil.nullToEmpty(question), MAX_QUESTION_CHARS),
                            "answer", StrUtil.subPre(StrUtil.nullToEmpty(answer), MAX_ANSWER_CHARS),
                            "count", String.valueOf(count)
                    )
            );
            prompt = prompt.replace("{chunks}", buildChunksText(chunks));

            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.7D)
                    .topP(0.8D)
                    .maxTokens(MAX_OUTPUT_TOKENS)
                    .thinking(false)
                    .build();
            String raw = llmService.chat(request, Tier.FAST);
            return parseQuestions(raw, count);
        } catch (Exception ex) {
            log.warn("生成推荐追问问题失败", ex);
            return RecommendedQuestionsPayload.failed();
        }
    }

    /**
     * Assemble grounding snippet text for prompt injection. With no snippets, a hint downgrades generation to the question and answer alone
     */
    private String buildChunksText(List<GroundingChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return "（无检索片段，仅依据问答生成）";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (GroundingChunk chunk : chunks) {
            if (chunk == null || StrUtil.isBlank(chunk.getText()) || sb.length() >= MAX_CHUNKS_CHARS) {
                continue;
            }
            String prefix = idx++ + ". 【" + StrUtil.nullToEmpty(chunk.getDocName()) + "】";
            int remaining = MAX_CHUNKS_CHARS - sb.length() - prefix.length() - 1;
            if (remaining <= 0) {
                break;
            }
            sb.append(prefix)
                    .append(StrUtil.subPre(chunk.getText(), remaining))
                    .append('\n');
        }
        return sb.isEmpty() ? "（无检索片段，仅依据问答生成）" : sb.toString().stripTrailing();
    }

    /**
     * Robust parse: strip code fences -> JSON array -> trim / drop blanks / dedupe / truncate. Any failure or non-array is treated as no result
     */
    private RecommendedQuestionsPayload parseQuestions(String raw, int count) {
        if (StrUtil.isBlank(raw)) {
            return RecommendedQuestionsPayload.failed();
        }
        String stripped = LLMResponseCleaner.stripMarkdownCodeFence(raw);
        if (StrUtil.isBlank(stripped)) {
            return RecommendedQuestionsPayload.failed();
        }
        try {
            JSONArray array = JSONUtil.parseArray(stripped);
            LinkedHashSet<String> dedup = new LinkedHashSet<>();
            for (Object item : array) {
                if (!(item instanceof CharSequence)) {
                    continue;
                }
                String text = StrUtil.subPre(StrUtil.trim(item.toString()), MAX_QUESTION_ITEM_CHARS);
                if (StrUtil.isNotBlank(text)) {
                    dedup.add(text);
                }
            }
            if (dedup.isEmpty()) {
                return RecommendedQuestionsPayload.empty();
            }
            List<String> result = new ArrayList<>(dedup);
            return RecommendedQuestionsPayload.success(
                    result.size() > count ? result.subList(0, count) : result);
        } catch (Exception ex) {
            log.warn("解析推荐追问问题失败，原文：{}", StrUtil.maxLength(raw, 200));
            return RecommendedQuestionsPayload.failed();
        }
    }

}
