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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.core.source.CitationMarkup;
import com.nageoffer.ai.ragent.rag.dto.RecommendedQuestionsPayload;
import com.nageoffer.ai.ragent.rag.service.RecommendedQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default recommended follow-up service
 * <p>
 * null means not generated yet, an empty array means generated but nothing suitable, and a non-empty array means success
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendedQuestionServiceImpl implements RecommendedQuestionService {

    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_USER = "user";

    private final ConversationMessageMapper conversationMessageMapper;
    private final RecommendedQuestionGenerator generator;

    @Override
    public RecommendedQuestionsPayload generate(String messageId, String userId) {
        ConversationMessageDO message = loadAssistantMessage(messageId, userId);
        if (isRecommendationDisabled(message)) {
            return RecommendedQuestionsPayload.empty();
        }

        List<String> cached = message.getRecommendedQuestions();
        if (cached != null) {
            return RecommendedQuestionsPayload.success(cached);
        }

        String question = loadQuestion(message);
        RecommendedQuestionsPayload generated =
                generator.generate(question, CitationMarkup.strip(message.getContent()), message.getRetrievedChunks());
        if (generated.status() == RecommendedQuestionsPayload.Status.FAILED) {
            return generated;
        }

        // Both SUCCESS and EMPTY are stored. The empty array is a valid negative cache
        ConversationMessageDO update = new ConversationMessageDO();
        update.setId(message.getId());
        update.setRecommendedQuestions(generated.questions());
        conversationMessageMapper.updateById(update);
        return generated;
    }

    /**
     * Locate the assistant message and check ownership (someone else's message, or a non-assistant message, is treated as missing)
     */
    private ConversationMessageDO loadAssistantMessage(String messageId, String userId) {
        ConversationMessageDO message = conversationMessageMapper.selectOne(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getId, messageId)
                        .eq(ConversationMessageDO::getUserId, userId)
                        .eq(ConversationMessageDO::getRole, ROLE_ASSISTANT)
                        .eq(ConversationMessageDO::getDeleted, 0)
        );
        if (message == null) {
            throw new ClientException("Message does not exist");
        }
        return message;
    }

    /**
     * Load the user question for this answer through the explicit replyToMessageId
     */
    private String loadQuestion(ConversationMessageDO message) {
        if (StrUtil.isBlank(message.getReplyToMessageId())) {
            return null;
        }
        ConversationMessageDO question = conversationMessageMapper.selectOne(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getId, message.getReplyToMessageId())
                        .eq(ConversationMessageDO::getConversationId, message.getConversationId())
                        .eq(ConversationMessageDO::getUserId, message.getUserId())
                        .eq(ConversationMessageDO::getRole, ROLE_USER)
                        .eq(ConversationMessageDO::getDeleted, 0)
        );
        return question == null ? null : question.getContent();
    }

    private boolean isRecommendationDisabled(ConversationMessageDO message) {
        return StrUtil.isNotBlank(message.getMessageStatus())
                && !ChatMessage.MessageStatus.NORMAL.name().equals(message.getMessageStatus());
    }
}
