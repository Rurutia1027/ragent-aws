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
public class KnowledgeDocumentUpdateRequest {

    /**
     * Document name
     */
    private String docName;

    /**
     * Processing mode: chunk / pipeline
     */
    private String processMode;


    /**
     * Ingestion config JSON (CHUNK mode), for example {"parseProfile":"fast","maxChars":1024,"overlapChars":128}.
     * Fields may be omitted. IngestionSpecCodec validates and normalizes them before storage
     */
    private String ingestionSpec;

    /**
     * Pipeline id (PIPELINE mode)
     */
    private String pipelineId;

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
}
