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

package com.nageoffer.ai.ragent.ingestion.controller.vo;

import com.nageoffer.ai.ragent.ingestion.domain.context.NodeLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Knowledge-base ingestion task view
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionTaskVO {

    /**
     * Task id
     */
    private String id;

    /**
     * Pipeline id
     */
    private String pipelineId;

    /**
     * Source type
     */
    private String sourceType;

    /**
     * Source location
     */
    private String sourceLocation;

    /**
     * Source file name
     */
    private String sourceFileName;

    /**
     * Task status
     */
    private String status;

    /**
     * Chunk count
     */
    private Integer chunkCount;

    /**
     * Error message
     */
    private String errorMessage;

    /**
     * Node run logs
     */
    private List<NodeLog> logs;

    /**
     * Metadata
     */
    private Map<String, Object> metadata;

    /**
     * Task start time
     */
    private Date startedAt;

    /**
     * Task finish time
     */
    private Date completedAt;

    /**
     * Created by
     */
    private String createdBy;

    /**
     * Created at
     */
    private Date createTime;

    /**
     * Updated at
     */
    private Date updateTime;
}
