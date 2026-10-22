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

import com.nageoffer.ai.ragent.rag.controller.request.ConversationUpdateRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.ConversationVO;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationCreateBO;

import java.util.List;

/**
 * Conversation service
 * Create, rename, and delete conversations
 */
public interface ConversationService {

    /**
     * List conversations for a user
     *
     * @param userId user ID
     * @return conversation views
     */
    List<ConversationVO> listByUserId(String userId);

    /**
     * Create or update a conversation
     * Update when the conversation ID in ConversationCreateBO already exists; otherwise create
     *
     * @param request create request
     */
    void createOrUpdate(ConversationCreateBO request);

    /**
     * Rename a conversation
     *
     * @param conversationId conversation ID
     * @param request        update request
     */
    void rename(String conversationId, ConversationUpdateRequest request);

    /**
     * Delete a conversation
     *
     * @param conversationId conversation ID
     */
    void delete(String conversationId);
}
