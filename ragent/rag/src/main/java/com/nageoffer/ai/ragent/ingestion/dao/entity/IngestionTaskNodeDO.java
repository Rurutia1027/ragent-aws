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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Knowledge-base ingestion task node entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_ingestion_task_node")
public class IngestionTaskNodeDO {

    /**
     * ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Task id
     */
    private String taskId;

    /**
     * Pipeline id
     */
    private String pipelineId;

    /**
     * Node id
     */
    private String nodeId;

    /**
     * Node type
     * such as fetcher, parser, chunker
     */
    private String nodeType;

    /**
     * Node order
     */
    private Integer nodeOrder;

    /**
     * Status (for example success, failed, skipped)
     */
    private String status;

    /**
     * Duration (milliseconds)
     */
    private Long durationMs;

    /**
     * Message
     */
    private String message;

    /**
     * Error message
     */
    private String errorMessage;

    /**
     * Output JSON
     */
    private String outputJson;

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
     * Delete flag
     */
    @TableLogic
    private Integer deleted;
}
