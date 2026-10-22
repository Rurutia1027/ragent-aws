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
import com.nageoffer.ai.ragent.framework.database.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Knowledge-base document entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_knowledge_document")
public class KnowledgeDocumentDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Owning knowledge-base id
     */
    private String kbId;

    /**
     * Document name
     */
    private String docName;

    /**
     * Source type: file / url
     */
    private String sourceType;

    /**
     * Source location (URL)
     */
    private String sourceLocation;

    /**
     * Scheduled fetch enabled: 1 enabled, 0 disabled
     */
    private Integer scheduleEnabled;

    /**
     * Schedule expression (cron)
     */
    private String scheduleCron;

    /**
     * Enabled: 1 enabled, 0 disabled
     */
    private Integer enabled;

    /**
     * Chunk count
     */
    private Integer chunkCount;

    /**
     * File location (a path on OSS / NFS or similar)
     */
    private String fileUrl;

    /**
     * File type: pdf / markdown / docx and so on
     */
    private String fileType;

    /**
     * Real MIME type
     * <p>
     * A different fact from the display {@link #fileType}. This one is detected from bytes and serves parse routing
     * only; that one comes from the extension and serves the icon and preview only. Treating the short display label
     * as a MIME type was the root of a whole class of crossed-wire bugs
     */
    private String mimeType;

    /**
     * File size (bytes)
     */
    private Long fileSize;

    /**
     * Processing mode: chunk / pipeline
     * - chunk: split directly with the chunk strategy
     * - pipeline: clean through a data channel
     */
    private String processMode;

    /**
     * Document-level ingestion config (JSON): parse profile + chunk budget.
     * Effective only when processMode=chunk
     * <p>
     * Replaces the old "chunk-strategy column + free JSON config column". The strategy enum never made a difference
     * on the real path, and the free JSON keys were assembled by hand in the UI and probed with hardcoded strings in three places on the backend
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String ingestionSpec;

    /**
     * Data channel (pipeline) id.
     * Effective only when processMode=pipeline
     */
    private String pipelineId;

    /**
     * Status:
     * - pending: waiting to be embedded
     * - running: embedding
     * - failed: embedding failed
     * - success: embedding finished
     */
    private String status;

    /**
     * Created by
     */
    private String createdBy;

    /**
     * Updated by
     */
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * Deleted: 0 normal, 1 deleted
     */
    @TableLogic
    private Integer deleted;
}
