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

package com.nageoffer.ai.ragent.rag.rewrite;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.core.rewrite.MultiQuestionRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryTermMappingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * History-selection contract at rewrite time: keep the summary and every historical question as-is, and keep only the latest two usable assistant replies
 * <p>
 * Most referents land on a subject the user already asked about. Drop a question and the entity cannot be recovered.
 * Assistant replies are the only expensive part of history, so they must be capped, and an interrupted or rate-limited fragment must not take a slot
 */
class MultiQuestionRewriteWindowTest {

    private static final String QUESTION = "它的上限是多少";
    private static final String LLM_JSON = """
            {"rewrite": "差旅报销的上限是多少", "sub_questions": ["差旅报销的上限是多少"]}
            """;

    private final LLMService llmService = mock(LLMService.class);
    private final QueryTermMappingService queryTermMappingService = mock(QueryTermMappingService.class);
    private final PromptTemplateLoader promptTemplateLoader = mock(PromptTemplateLoader.class);

    /**
     * Ordinary multi-turn: the summary stays in place, no historical question is dropped, and only the latest two assistant replies remain
     */
    @Test
    void keepsSummaryAndEveryUserQuestionButOnlyLastTwoAnswers() {
        List<ChatMessage> history = List.of(
                ChatMessage.system("<conversation-summary>用户在咨询差旅报销</conversation-summary>"),
                ChatMessage.user("差旅报销走什么流程"),
                ChatMessage.assistant("先在 OA 提交申请单"),
                ChatMessage.user("需要哪些材料"),
                ChatMessage.assistant("发票和审批单"),
                ChatMessage.user("多久能到账"),
                ChatMessage.assistant("审批通过后三个工作日"));

        List<ChatMessage> sent = captureHistorySentToLlm(history);

        assertThat(contentsOf(sent)).containsExactly(
                "<conversation-summary>用户在咨询差旅报销</conversation-summary>",
                "差旅报销走什么流程",
                "需要哪些材料",
                "发票和审批单",
                "多久能到账",
                "审批通过后三个工作日");
    }

    /**
     * The summary has the SYSTEM role and must be kept when fed to rewrite, or entities from early turns that were compressed away cannot be resolved
     */
    @Test
    void keepsSummaryMessage() {
        List<ChatMessage> history = List.of(
                ChatMessage.system("<conversation-summary>用户在咨询差旅报销</conversation-summary>"),
                ChatMessage.user("差旅报销走什么流程"),
                ChatMessage.assistant("先在 OA 提交申请单"));

        List<ChatMessage> sent = captureHistorySentToLlm(history);

        assertThat(sent).first()
                .satisfies(msg -> assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.SYSTEM));
    }

    /**
     * An interrupted partial answer does not take a slot. The slot goes to the complete answer further back
     */
    @Test
    void skipsInterruptedAnswerAndFallsBackToEarlierOne() {
        List<ChatMessage> history = List.of(
                ChatMessage.user("差旅报销走什么流程"),
                ChatMessage.assistant("先在 OA 提交申请单"),
                ChatMessage.user("需要哪些材料"),
                assistantWith("发票和审", ChatMessage.MessageStatus.INTERRUPTED),
                ChatMessage.user("多久能到账"),
                assistantWith("审批通过后三", ChatMessage.MessageStatus.INTERRUPTED));

        List<ChatMessage> sent = captureHistorySentToLlm(history);

        assertThat(contentsOf(sent)).containsExactly(
                "差旅报销走什么流程",
                "先在 OA 提交申请单",
                "需要哪些材料",
                "多久能到账");
    }

    /**
     * Queue boilerplate stored for a rate limit also takes no slot. Resolving a reference from it only pulls in an unrelated entity
     */
    @Test
    void skipsRejectedAnswer() {
        List<ChatMessage> history = List.of(
                ChatMessage.user("差旅报销走什么流程"),
                ChatMessage.assistant("先在 OA 提交申请单"),
                ChatMessage.user("需要哪些材料"),
                assistantWith("当前排队人数过多，请稍后再试", ChatMessage.MessageStatus.REJECTED));

        List<ChatMessage> sent = captureHistorySentToLlm(history);

        assertThat(contentsOf(sent)).containsExactly(
                "差旅报销走什么流程",
                "先在 OA 提交申请单",
                "需要哪些材料");
    }

    /**
     * The user stops before the first character, that turn stores nothing, and history then has two questions in a row
     */
    @Test
    void keepsConsecutiveUserQuestionsCausedByStop() {
        List<ChatMessage> history = List.of(
                ChatMessage.user("差旅报销走什么流程"),
                ChatMessage.user("需要哪些材料"),
                ChatMessage.assistant("发票和审批单"));

        List<ChatMessage> sent = captureHistorySentToLlm(history);

        assertThat(contentsOf(sent)).containsExactly(
                "差旅报销走什么流程",
                "需要哪些材料",
                "发票和审批单");
    }

    /**
     * Two stops in a row are three questions with only one answer. A per-turn quota would drop that only answer; counting messages keeps it
     */
    @Test
    void keepsTheOnlyAnswerWhenLaterTurnsWereAllStopped() {
        List<ChatMessage> history = List.of(
                ChatMessage.user("差旅报销走什么流程"),
                ChatMessage.assistant("先在 OA 提交申请单"),
                ChatMessage.user("需要哪些材料"),
                ChatMessage.user("多久能到账"));

        List<ChatMessage> sent = captureHistorySentToLlm(history);

        assertThat(contentsOf(sent)).containsExactly(
                "差旅报销走什么流程",
                "先在 OA 提交申请单",
                "需要哪些材料",
                "多久能到账");
    }

    /**
     * However long the history, questions are not truncated. How many turns the rewrite window spans is decided by rag.memory.history-keep-turns
     */
    @Test
    void doesNotTruncateLongUserQuestionHistory() {
        List<ChatMessage> history = List.of(
                ChatMessage.user("第一问"),
                ChatMessage.assistant("第一答"),
                ChatMessage.user("第二问"),
                ChatMessage.assistant("第二答"),
                ChatMessage.user("第三问"),
                ChatMessage.assistant("第三答"),
                ChatMessage.user("第四问"),
                ChatMessage.assistant("第四答"));

        List<ChatMessage> sent = captureHistorySentToLlm(history);

        assertThat(contentsOf(sent)).containsExactly(
                "第一问", "第二问", "第三问", "第三答", "第四问", "第四答");
    }

    /**
     * The last message is always the current question. The prompt line "only the last user message is the current question" depends on that
     */
    @Test
    void putsCurrentQuestionLast() {
        List<ChatMessage> history = List.of(
                ChatMessage.user("差旅报销走什么流程"),
                ChatMessage.assistant("先在 OA 提交申请单"));

        List<ChatMessage> messages = captureRequestSentToLlm(history).getMessages();

        assertThat(messages).last().satisfies(msg -> {
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.USER);
            assertThat(msg.getContent()).isEqualTo(QUESTION);
        });
        assertThat(messages).first()
                .satisfies(msg -> assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.SYSTEM));
    }

    /**
     * First turn, no history: send only the system prompt and the current question
     */
    @Test
    void sendsOnlyPromptAndQuestionWhenHistoryIsEmpty() {
        List<ChatMessage> messages = captureRequestSentToLlm(List.of()).getMessages();

        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).getContent()).isEqualTo(QUESTION);
    }

    private static ChatMessage assistantWith(String content, ChatMessage.MessageStatus status) {
        return new ChatMessage(ChatMessage.Role.ASSISTANT, content, status);
    }

    private static List<String> contentsOf(List<ChatMessage> messages) {
        return messages.stream().map(ChatMessage::getContent).toList();
    }

    /**
     * Capture the request actually sent to the model and take only its history (drop the system prompt and the final current question)
     */
    private List<ChatMessage> captureHistorySentToLlm(List<ChatMessage> history) {
        List<ChatMessage> messages = captureRequestSentToLlm(history).getMessages();
        return messages.subList(1, messages.size() - 1);
    }

    private ChatRequest captureRequestSentToLlm(List<ChatMessage> history) {
        RAGConfigProperties properties = new RAGConfigProperties();
        properties.setQueryRewriteEnabled(true);
        when(queryTermMappingService.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(promptTemplateLoader.load(anyString())).thenReturn("系统提示词");
        when(llmService.chat(any(ChatRequest.class), eq(Tier.FAST))).thenReturn(LLM_JSON);

        MultiQuestionRewriteService service = new MultiQuestionRewriteService(
                llmService, properties, queryTermMappingService, promptTemplateLoader);
        service.rewriteWithSplit(QUESTION, history);

        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).chat(request.capture(), eq(Tier.FAST));
        return request.getValue();
    }
}
