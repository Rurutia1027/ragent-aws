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

package com.nageoffer.ai.ragent.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * RAG knowledge-base document chunk row
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_knowledge_chunk")
public class KnowledgeChunkDO {

    /**
     * ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Knowledge-base id
     */
    private String kbId;

    /**
     * Document id
     */
    private String docId;

    /**
     * Chunk index (starting at 0)
     */
    private Integer chunkIndex;

    /**
     * Chunk body
     */
    private String content;

    /**
     * Content hash (idempotency / dedup)
     */
    private String contentHash;

    /**
     * Character count (stats / tuning)
     */
    private Integer charCount;

    /**
     * Token count (optional)
     */
    private Integer tokenCount;

    /**
     * Embedding text: outline path + body
     * <p>
     * Stored not for display. It is the only correct source for rebuilding a vector: structure (outline path, a
     * table's {@code column: value} rendering, an image description with the URL removed) lives only in this column.
     * Recomputing from content silently drops it
     */
    private String embeddingText;

    /**
     * Enabled. 0: disabled, 1: enabled
     */
    private Integer enabled;

    /**
     * Created by
     */
    private String createdBy;

    /**
     * Updated by
     */
    private String updatedBy;

    /**
     * Created at
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * Updated at
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * Deleted. 0: normal, 1: deleted
     */
    @TableLogic
    private Integer deleted;
}
