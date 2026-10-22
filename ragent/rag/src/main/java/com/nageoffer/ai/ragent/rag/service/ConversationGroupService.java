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

import com.nageoffer.ai.ragent.rag.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationSummaryDO;

import java.util.Date;
import java.util.List;

/**
 * Conversation group service
 * Queries conversation messages, summaries, and conversation records
 */
public interface ConversationGroupService {

    /**
     * Latest user messages in the given conversation
     *
     * @param conversationId conversation ID
     * @param userId         user ID
     * @param limit          maximum number of messages to return
     * @return user messages, newest first
     */
    List<ConversationMessageDO> listLatestUserOnlyMessages(String conversationId, String userId, int limit);

    /**
     * Messages whose IDs fall in the given range
     *
     * @param conversationId conversation ID
     * @param userId         user ID
     * @param afterId        start message ID (exclusive)
     * @param beforeId       end message ID (exclusive)
     * @return messages in that range
     */
    List<ConversationMessageDO> listMessagesBetweenIds(String conversationId, String userId, String afterId, String beforeId);

    /**
     * Largest message ID at or before the given time
     *
     * @param conversationId conversation ID
     * @param userId         user ID
     * @param at             the time
     * @return the largest message ID, or null if none exists
     */
    String findMaxMessageIdAtOrBefore(String conversationId, String userId, Date at);

    /**
     * Count the user's messages in the given conversation
     *
     * @param conversationId conversation ID
     * @param userId         user ID
     * @return total user message count
     */
    long countUserMessages(String conversationId, String userId);

    /**
     * Latest summary for the given conversation
     *
     * @param conversationId conversation ID
     * @param userId         user ID
     * @return the latest summary, or null if none exists
     */
    ConversationSummaryDO findLatestSummary(String conversationId, String userId);

    /**
     * Find the given conversation
     *
     * @param conversationId conversation ID
     * @param userId         user ID
     * @return the conversation, or null if it does not exist
     */
    ConversationDO findConversation(String conversationId, String userId);
}
