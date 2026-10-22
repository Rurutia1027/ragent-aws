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

package com.nageoffer.ai.ragent.ingestion.node;

import com.nageoffer.ai.ragent.ingestion.domain.context.IngestionContext;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import com.nageoffer.ai.ragent.ingestion.domain.result.NodeResult;

/**
 * Ingestion node. The basic unit of the data ingestion flow.
 * Each node runs one piece of processing
 */
public interface IngestionNode {

    /**
     * Returns the node type id
     *
     * @return string form of the node type
     */
    String getNodeType();

    /**
     * Runs the node's logic
     *
     * @param context ingestion context, holding shared state and data
     * @param config  config of the current node
     * @return result of running the node
     */
    NodeResult execute(IngestionContext context, NodeConfig config);
}
