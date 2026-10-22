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

package com.nageoffer.ai.ragent.ingestion.controller.request;

import com.nageoffer.ai.ragent.rag.controller.request.DocumentSourceRequest;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceId;
import lombok.Data;

import java.util.Map;

/**
 * Request to create an ingestion task.
 * Receives the parameters for a new task: pipeline id, document source, and metadata.
 */
@Data
public class IngestionTaskCreateRequest {

    /**
     * Pipeline id that runs this ingestion
     */
    private String pipelineId;

    /**
     * Document source
     */
    private DocumentSourceRequest source;

    /**
     * Metadata for the ingestion task.
     * Custom extra key-value attributes
     */
    private Map<String, Object> metadata;

    /**
     * Vector space id: the target collection the vectors are written to.
     * When omitted, the default vector space is used
     */
    private VectorSpaceId vectorSpaceId;
}
