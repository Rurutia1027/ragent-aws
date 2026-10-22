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
 * Knowledge-base chunk view
 */
@Data
public class KnowledgeChunkVO {

    /**
     * ID
     */
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
     * Character count
     */
    private Integer charCount;

    /**
     * Token count
     */
    private Integer tokenCount;

    /**
     * Enabled. 0: disabled, 1: enabled
     */
    private Integer enabled;

    /**
     * Created at
     */
    private LocalDateTime createTime;

    /**
     * Updated at
     */
    private LocalDateTime updateTime;
}
