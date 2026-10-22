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

package com.nageoffer.ai.ragent.knowledge.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Knowledge-base document view
 */
@Data
public class KnowledgeDocumentVO {

    /**
     * Unique document id
     */
    private String id;

    /**
     * Knowledge-base id
     */
    private String kbId;

    /**
     * Document name
     */
    private String docName;

    /**
     * Source type
     */
    private String sourceType;

    /**
     * Source location
     */
    private String sourceLocation;

    /**
     * Whether scheduled fetch is enabled
     */
    private Integer scheduleEnabled;

    /**
     * Schedule expression
     */
    private String scheduleCron;

    /**
     * Whether enabled
     */
    private Boolean enabled;

    /**
     * Chunk count
     */
    private Integer chunkCount;

    /**
     * File URL
     */
    private String fileUrl;

    /**
     * File type
     */
    private String fileType;

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
     * Document-level ingestion config (JSON): parse profile + chunk budget
     */
    private String ingestionSpec;

    /**
     * Data channel (pipeline) id.
     * Effective only when processMode=pipeline
     */
    private String pipelineId;

    /**
     * Status (for example parsing, parsed, parse failed)
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

    /**
     * Created at
     */
    private LocalDateTime createTime;

    /**
     * Updated at
     */
    private LocalDateTime updateTime;

    /**
     * Whether any chunk was edited by hand (inferred from chunk.updateTime > chunk.createTime).
     * Filled only on query, not persisted. Re-chunking clears it
     */
    private Boolean chunksEdited;
}
