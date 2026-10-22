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

package com.nageoffer.ai.ragent.rag.service.bo;

import com.nageoffer.ai.ragent.framework.convention.GroundingChunk;
import com.nageoffer.ai.ragent.framework.convention.SourceRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Conversation message business object
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMessageBO {

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * User ID
     */
    private String userId;

    /**
     * Role: system/user/assistant
     */
    private String role;

    /**
     * Message content
     */
    private String content;

    /**
     * Deep-thinking content
     */
    private String thinkingContent;

    /**
     * Deep-thinking duration (seconds)
     */
    private Integer thinkingDuration;

    /**
     * Answer sources, a document-level source list (only assistant messages may have it)
     */
    private List<SourceRef> sources;

    /**
     * Grounding snippets for recommended questions (only assistant messages may have them; used to ground follow-up generation)
     */
    private List<GroundingChunk> retrievedChunks;

    /**
     * User message ID that this assistant message answers
     */
    private String replyToMessageId;

    /**
     * Message end status: NORMAL=completed, INTERRUPTED=user cancelled, REJECTED=rate limited
     */
    private String messageStatus;
}
