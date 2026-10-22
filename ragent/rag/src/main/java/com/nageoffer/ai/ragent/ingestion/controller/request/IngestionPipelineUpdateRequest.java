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

import lombok.Data;

import java.util.List;

/**
 * Request to update an ingestion pipeline.
 * Receives the parameters for an existing pipeline: name, description, and node configs
 */
@Data
public class IngestionPipelineUpdateRequest {

    /**
     * Pipeline name
     */
    private String name;

    /**
     * Pipeline description
     */
    private String description;

    /**
     * Pipeline node configs
     */
    private List<IngestionPipelineNodeRequest> nodes;
}
