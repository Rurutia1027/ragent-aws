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

package com.nageoffer.ai.ragent.ingestion.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nageoffer.ai.ragent.framework.database.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Knowledge-base ingestion task entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_ingestion_task")
public class IngestionTaskDO {

    /**
     * Primary key id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Pipeline id
     */
    private String pipelineId;

    /**
     * Source type (for example file, url, feishu, s3)
     */
    private String sourceType;

    /**
     * Source location (file path or URL)
     */
    private String sourceLocation;

    /**
     * Source file name
     */
    private String sourceFileName;

    /**
     * Task status (for example pending, running, completed, failed)
     */
    private String status;

    /**
     * Chunk count
     */
    private Integer chunkCount;

    /**
     * Error detail
     */
    private String errorMessage;

    /**
     * Log JSON
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String logsJson;

    /**
     * Metadata JSON
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadataJson;

    /**
     * Start time
     */
    private Date startedAt;

    /**
     * Finish time
     */
    private Date completedAt;

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
     * Delete flag (0: not deleted, 1: deleted)
     */
    @TableLogic
    private Integer deleted;
}
