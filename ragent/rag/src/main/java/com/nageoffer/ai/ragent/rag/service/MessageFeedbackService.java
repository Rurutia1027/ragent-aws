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

import com.nageoffer.ai.ragent.rag.controller.request.MessageFeedbackRequest;
import com.nageoffer.ai.ragent.rag.mq.event.MessageFeedbackEvent;

import java.util.List;
import java.util.Map;

public interface MessageFeedbackService {

    /**
     * Submit conversation-message feedback (synchronous, for direct internal calls)
     *
     * @param messageId message ID
     * @param request   feedback body
     */
    void submitFeedback(String messageId, MessageFeedbackRequest request);

    /**
     * Submit conversation-message feedback (async, persisted through MQ)
     *
     * @param messageId message ID
     * @param request   feedback body
     */
    void submitFeedbackAsync(String messageId, MessageFeedbackRequest request);

    /**
     * Cancel conversation-message feedback (async, persisted through MQ)
     *
     * @param messageId message ID
     */
    void cancelFeedbackAsync(String messageId);

    /**
     * Persist feedback asynchronously from an MQ event (called by the consumer)
     *
     * @param event feedback event
     */
    void submitFeedbackByEvent(MessageFeedbackEvent event);

    /**
     * Look up the user's feedback values for a batch of messages
     *
     * @param userId     user ID
     * @param messageIds message ID list
     * @return messageId -> vote
     */
    Map<String, Integer> getUserVotes(String userId, List<String> messageIds);
}
