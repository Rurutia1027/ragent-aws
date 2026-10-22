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

package com.nageoffer.ai.ragent.ingestion.domain.result;

import com.nageoffer.ai.ragent.ingestion.domain.enums.IngestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ingestion result.
 * Summary after a document ingestion task finishes: status, chunk count, and similar
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionResult {

    /**
     * Unique id of the ingestion task
     */
    private String taskId;

    /**
     * Pipeline id that ran this ingestion
     */
    private String pipelineId;

    /**
     * Final status of the ingestion task
     */
    private IngestionStatus status;

    /**
     * Number of chunks the document was split into
     */
    private Integer chunkCount;

    /**
     * Message for the result.
     * A summary on success, the error reason on failure
     */
    private String message;
}
