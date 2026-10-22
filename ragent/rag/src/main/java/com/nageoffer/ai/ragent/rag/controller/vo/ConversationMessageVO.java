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

package com.nageoffer.ai.ragent.rag.controller.vo;

import com.nageoffer.ai.ragent.framework.convention.SourceRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Conversation message view
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMessageVO {

    /**
     * Message ID
     */
    private String id;

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * Role (for example: user, assistant)
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
     * Feedback value: 1=like, -1=dislike, null=no feedback
     */
    private Integer vote;

    /**
     * Answer sources, a document-level source list (only assistant messages may have it)
     */
    private List<SourceRef> sources;

    /**
     * Recommended follow-up questions, generated lazily after the answer (only assistant messages may have them)
     */
    private List<String> recommendedQuestions;

    /**
     * Message end status: NORMAL=completed, INTERRUPTED=user cancelled, REJECTED=rate limited
     */
    private String messageStatus;

    /**
     * Created time
     */
    private Date createTime;
}
