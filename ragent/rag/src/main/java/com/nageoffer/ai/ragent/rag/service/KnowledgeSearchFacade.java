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

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.core.guidance.GuidanceDecision;
import com.nageoffer.ai.ragent.rag.core.guidance.IntentGuidanceService;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolver;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptContext;
import com.nageoffer.ai.ragent.rag.core.prompt.RAGPromptService;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalEngine;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.core.source.CitationContextEnricher;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Knowledge search facade: the only narrow retrieval entry rag exposes in Agent mode
 * Rewrite -> KB intent resolution -> ambiguity guidance -> multi-channel retrieval -> KB_ANSWER synthesis, returning answer text that can be cited directly
 * Citation and source assembly are fixed off, independent of rag.citation.enabled
 * No conversation history: the main Agent has already resolved references, and synthesis uses only this turn's evidence
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchFacade {

    private static final String EMPTY_RESULT = "未在知识库中检索到与该问题相关的内容。";

    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final CitationContextEnricher citationContextEnricher;
    private final RAGPromptService promptService;
    private final LLMService llmService;

    /**
     * Retrieve and synthesize an answer, for the main Agent's search_knowledge tool
     */
    public String search(String query) {
        // Do not feed history: the main Agent holds the full conversation, and the query it passes in is already resolved and deliberately narrowed
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(query, List.of());
        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewriteResult);

        GuidanceDecision guidance = guidanceService.detectAmbiguity(
                rewriteResult.rewrittenQuestion(), subIntents);
        if (guidance.isPrompt()) {
            log.info("Agent 知识库检索命中歧义引导，跳过检索与答案合成, question={}",
                    rewriteResult.rewrittenQuestion());
            return guidance.getPrompt();
        }

        RetrievalContext retrievalCtx = retrievalEngine.retrieve(subIntents);
        if (!retrievalCtx.hasKb()) {
            return EMPTY_RESULT;
        }

        // The tool does not render citation markers, but internal docIds must still be stripped or they leak into the main Agent's visible text with the tool result
        String kbContext = citationContextEnricher.stripDocIdAnchors(retrievalCtx.getKbContext());

        PromptContext promptContext = PromptContext.builder()
                .kbContext(kbContext)
                .kbIntents(intentResolver.mergeKbIntents(subIntents))
                .eligibleIntentIds(retrievalCtx.getEligibleIntentIds())
                .build();
        List<ChatMessage> messages = promptService.buildStructuredMessages(
                promptContext, List.of(), rewriteResult.rewrittenQuestion(), rewriteResult.subQuestions(), false);

        return llmService.chat(ChatRequest.builder()
                .messages(messages)
                .temperature(0D)
                .topP(1D)
                .thinking(false)
                .build());
    }
}
