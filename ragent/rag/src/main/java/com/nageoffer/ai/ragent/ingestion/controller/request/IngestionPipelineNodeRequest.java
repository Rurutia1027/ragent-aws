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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Request for one ingestion pipeline node.
 * Receives one node's config: id, type, settings, and execution condition
 */
@Data
public class IngestionPipelineNodeRequest {

    /**
     * Unique id of the node
     */
    private String nodeId;

    /**
     * Node type.
     * For example fetcher, parser, chunker, indexer
     */
    private String nodeType;

    /**
     * Node settings.
     * Each node type has its own config shape
     */
    private JsonNode settings;

    /**
     * Condition expression for running the node.
     * The node runs only when the condition holds
     */
    private JsonNode condition;

    /**
     * Next node id.
     * Defines execution order in the pipeline
     */
    private String nextNodeId;
}
