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

package com.nageoffer.ai.ragent.rag.core.rewrite;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.infra.util.LLMResponseCleaner;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.QUERY_REWRITE_AND_SPLIT_PROMPT_PATH;

/**
 * Query preprocessing: rewrite and split multi-questions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiQuestionRewriteService implements QueryRewriteService {

    /**
     * Upper bound on assistant replies fed to rewrite
     */
    private static final int MAX_ASSISTANT_MESSAGES = 2;

    private final LLMService llmService;
    private final RAGConfigProperties ragConfigProperties;
    private final QueryTermMappingService queryTermMappingService;
    private final PromptTemplateLoader promptTemplateLoader;

    @Override
    @RagTraceNode(name = "query-rewrite", type = "REWRITE")
    public String rewrite(String userQuestion) {
        return rewriteWithSplit(userQuestion).rewrittenQuestion();
    }

    @Override
    public RewriteResult rewriteWithSplit(String userQuestion) {
        return rewriteWithSplit(userQuestion, List.of());
    }

    @Override
    @RagTraceNode(name = "query-rewrite-and-split", type = "REWRITE")
    public RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history) {
        if (!ragConfigProperties.getQueryRewriteEnabled()) {
            String normalized = queryTermMappingService.normalize(userQuestion);
            List<String> subs = ruleBasedSplit(normalized);
            return new RewriteResult(normalized, subs);
        }

        String normalizedQuestion = queryTermMappingService.normalize(userQuestion);

        return callLLMRewriteAndSplit(normalizedQuestion, userQuestion, history);
    }

    private RewriteResult callLLMRewriteAndSplit(String normalizedQuestion,
                                                 String originalQuestion,
                                                 List<ChatMessage> history) {
        String systemPrompt = promptTemplateLoader.load(QUERY_REWRITE_AND_SPLIT_PROMPT_PATH);
        List<ChatMessage> rewriteHistory = selectRewriteHistory(history);
        ChatRequest req = buildRewriteRequest(systemPrompt, normalizedQuestion, rewriteHistory);

        // Fast-tier call. Parse failure or call failure both fall back to the normalized question (the tier's multiple candidates already cover transport faults; do not escalate across tiers)
        RewriteResult fallback = new RewriteResult(normalizedQuestion, List.of(normalizedQuestion));
        RewriteResult result;
        try {
            RewriteResult parsed = parseRewriteAndSplit(llmService.chat(req, Tier.FAST));
            result = parsed != null ? parsed : fallback;
        } catch (Exception e) {
            log.warn("查询改写 LLM 调用失败，使用归一化问题兜底", e);
            result = fallback;
        }

        log.info("""
                RAG用户问题查询改写+拆分：
                原始问题：{}
                归一化后：{}
                改写结果：{}
                子问题：{}
                """, originalQuestion, normalizedQuestion, result.rewrittenQuestion(), result.subQuestions());
        return result;
    }

    /**
     * Select history fed to rewrite: keep the summary and every historical user question as-is, and keep only the latest {@link #MAX_ASSISTANT_MESSAGES} assistant replies
     * <p>
     * Most referents land on a subject the user already asked about, so every historical question is kept or a cross-turn reference cannot be resolved.
     * Assistant replies are borrowed for entities only in the immediately neighboring turns. Anything beyond two kept replies is pure cost and spreads unrelated topics into the rewrite model, mixing entities
     * <p>
     * Only drop, do not reorder. The upstream guarantee of "starts with USER, ascending by time" still holds
     */
    private List<ChatMessage> selectRewriteHistory(List<ChatMessage> history) {
        if (CollUtil.isEmpty(history)) {
            return List.of();
        }
        Deque<ChatMessage> selected = new ArrayDeque<>(history.size());
        int remainingAssistantMessages = MAX_ASSISTANT_MESSAGES;
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.getRole() == ChatMessage.Role.ASSISTANT) {
                if (remainingAssistantMessages == 0 || !isUsableAssistant(message)) {
                    continue;
                }
                remainingAssistantMessages--;
            }
            selected.addFirst(message);
        }
        return new ArrayList<>(selected);
    }

    /**
     * An assistant message stored for an interrupt or a rate limit does not take a slot: the first is a partial answer, the second is boilerplate such as "too many people in the queue".
     * Resolving a reference from them only pulls a broken or unrelated entity into rewrite. The slot is better given to the real answer further back.
     * A missing status is treated as normal. Prefer carrying one extra message over misjudging a real answer
     */
    private boolean isUsableAssistant(ChatMessage message) {
        ChatMessage.MessageStatus status = message.getMessageStatus();
        return status == null || status == ChatMessage.MessageStatus.NORMAL;
    }

    private ChatRequest buildRewriteRequest(String systemPrompt,
                                            String question,
                                            List<ChatMessage> rewriteHistory) {
        List<ChatMessage> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        messages.addAll(rewriteHistory);
        // The last message is always the current question. The prompt's "message structure" section locates it from that. Do not append anything after it
        messages.add(ChatMessage.user(question));

        return ChatRequest.builder()
                .messages(messages)
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();
    }


    private RewriteResult parseRewriteAndSplit(String raw) {
        try {
            // Strip a Markdown code-fence marker if one is present
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);

            JsonElement root = JsonParser.parseString(cleaned);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject obj = root.getAsJsonObject();
            String rewrite = obj.has("rewrite") ? obj.get("rewrite").getAsString().trim() : "";
            List<String> subs = new ArrayList<>();
            if (obj.has("sub_questions") && obj.get("sub_questions").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("sub_questions");
                for (JsonElement el : arr) {
                    if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                        String s = el.getAsString().trim();
                        if (StrUtil.isNotBlank(s)) {
                            subs.add(s);
                        }
                    }
                }
            }
            if (StrUtil.isBlank(rewrite)) {
                return null;
            }
            if (CollUtil.isEmpty(subs)) {
                subs = List.of(rewrite);
            }
            return new RewriteResult(rewrite, subs);
        } catch (Exception e) {
            log.warn("解析改写+拆分结果失败，raw={}", raw, e);
            return null;
        }
    }

    private List<String> ruleBasedSplit(String question) {
        // Fallback: split on common separators
        List<String> parts = Arrays.stream(question.split("[?？。；;\\n]+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(parts)) {
            return List.of(question);
        }
        return parts.stream()
                .map(s -> s.endsWith("？") || s.endsWith("?") ? s : s + "？")
                .toList();
    }
}
