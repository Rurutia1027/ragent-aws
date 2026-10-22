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

package com.nageoffer.ai.ragent.rag.service.handler;

import com.nageoffer.ai.ragent.framework.web.StreamTaskManager;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import com.nageoffer.ai.ragent.rag.service.ConversationGroupService;
import lombok.Builder;
import lombok.Getter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Build parameters for StreamChatEventHandler
 * A parameter object that bundles the arguments
 */
@Getter
@Builder
public class StreamChatHandlerParams {

    /**
     * SSE emitter
     */
    private final SseEmitter emitter;

    /**
     * Conversation ID
     */
    private final String conversationId;

    /**
     * Task ID
     */
    private final String taskId;

    /**
     * Model configuration
     */
    private final AIModelProperties modelProperties;

    /**
     * Memory service
     */
    private final ConversationMemoryService memoryService;

    /**
     * Conversation group service
     */
    private final ConversationGroupService conversationGroupService;

    /**
     * Task manager
     */
    private final StreamTaskManager taskManager;
}
