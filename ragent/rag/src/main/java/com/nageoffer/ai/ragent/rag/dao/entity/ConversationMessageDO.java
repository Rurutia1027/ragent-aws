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

package com.nageoffer.ai.ragent.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nageoffer.ai.ragent.framework.convention.GroundingChunk;
import com.nageoffer.ai.ragent.framework.convention.SourceRef;
import com.nageoffer.ai.ragent.knowledge.dao.handler.GroundingChunkListTypeHandler;
import com.nageoffer.ai.ragent.knowledge.dao.handler.SourceRefListTypeHandler;
import com.nageoffer.ai.ragent.knowledge.dao.handler.StringListTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Conversation message entity
 * Stores messages from a conversation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_message", autoResultMap = true)
public class ConversationMessageDO {

    /**
     * Primary key, generated with the snowflake algorithm
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Conversation ID, linking the message to a conversation
     */
    private String conversationId;

    /**
     * User ID of the message sender
     */
    private String userId;

    /**
     * Role: user/assistant
     * user: user message
     * assistant: assistant reply
     */
    private String role;

    /**
     * Message content, the actual message text
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
     * Answer sources, a document-level source list (stored as jsonb; only assistant messages may have it)
     */
    @TableField(typeHandler = SourceRefListTypeHandler.class)
    private List<SourceRef> sources;

    /**
     * Grounding snippets for recommended questions (stored as jsonb; only assistant messages may have them; persisted with the message so follow-up generation can ground, not part of the model context)
     */
    @TableField(typeHandler = GroundingChunkListTypeHandler.class)
    private List<GroundingChunk> retrievedChunks;

    /**
     * Recommended follow-up questions, generated lazily after the answer (stored as jsonb; only assistant messages may have them; not part of the model context)
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> recommendedQuestions;

    /**
     * User message ID that this assistant message answers
     */
    private String replyToMessageId;

    /**
     * Message end status: NORMAL=completed, INTERRUPTED=user cancelled, REJECTED=rate limited
     */
    private String messageStatus;

    /**
     * Created time, filled automatically
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * Updated time, filled automatically on insert and update
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * Delete flag, logical-delete column
     */
    @TableLogic
    private Integer deleted;
}
