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
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Knowledge-base document chunk log
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_knowledge_document_chunk_log")
public class KnowledgeDocumentChunkLogDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Document id
     */
    private String docId;

    /**
     * Run status: running / success / failed
     */
    private String status;

    /**
     * Processing mode: chunk / pipeline
     */
    private String processMode;

    /**
     * Parse profile (chunk mode only): confirms, when debugging, whether this document used the fast or fidelity profile
     */
    private String parseProfile;

    /**
     * Pipeline id (pipeline mode only)
     */
    private String pipelineId;

    /**
     * Text extraction duration (milliseconds)
     */
    private Long extractDuration;

    /**
     * Chunking duration (milliseconds)
     */
    private Long chunkDuration;

    /**
     * Embedding API duration (milliseconds)
     */
    private Long embedDuration;

    /**
     * DB persist duration (milliseconds)
     */
    private Long persistDuration;

    /**
     * Total duration (milliseconds)
     */
    private Long totalDuration;

    /**
     * Number of chunks produced
     */
    private Integer chunkCount;

    /**
     * Error message
     */
    private String errorMessage;

    /**
     * Start time
     */
    private Date startTime;

    /**
     * End time
     */
    private Date endTime;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
