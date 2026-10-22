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

package com.nageoffer.ai.ragent.knowledge.controller.request;

import lombok.Data;

@Data
public class KnowledgeDocumentUploadRequest {

    /**
     * Source type: file / url
     */
    private String sourceType;

    /**
     * Source location (URL)
     */
    private String sourceLocation;

    /**
     * Whether scheduled fetch is enabled
     */
    private Boolean scheduleEnabled;

    /**
     * Schedule expression (cron)
     */
    private String scheduleCron;

    /**
     * Processing mode: chunk / pipeline
     * - chunk: split directly with the chunk strategy
     * - pipeline: clean through a data channel
     */
    private String processMode;

    /**
     * Document-level ingestion config JSON. Effective only when processMode=chunk
     * <p>
     * Shaped like {@code {"parseProfile":"fast","maxChars":512,"overlapChars":64,"rowsPerChunk":50}}.
     * Omitted fields use the system default budget
     */
    private String ingestionSpec;

    /**
     * Data channel (pipeline) id.
     * Effective only when processMode=pipeline
     */
    private String pipelineId;
}
