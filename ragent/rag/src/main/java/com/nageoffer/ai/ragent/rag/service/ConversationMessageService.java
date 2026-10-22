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

import com.nageoffer.ai.ragent.rag.controller.vo.ConversationMessageVO;
import com.nageoffer.ai.ragent.rag.enums.ConversationMessageOrder;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationMessageBO;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationSummaryBO;

import java.util.List;

public interface ConversationMessageService {

    /**
     * Add a conversation message
     *
     * @param conversationMessage message body
     */
    String addMessage(ConversationMessageBO conversationMessage);

    /**
     * List conversation messages (with sort order and a count limit)
     *
     * @param conversationId conversation ID
     * @param userId         user ID
     * @param limit          maximum count
     * @param order          sort order
     * @return conversation messages
     */
    List<ConversationMessageVO> listMessages(String conversationId, String userId, Integer limit, ConversationMessageOrder order);

    /**
     * Add a conversation summary
     *
     * @param conversationSummary conversation summary
     */
    void addMessageSummary(ConversationSummaryBO conversationSummary);
}
