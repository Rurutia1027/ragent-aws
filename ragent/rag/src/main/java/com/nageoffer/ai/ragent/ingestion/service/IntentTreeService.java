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

package com.nageoffer.ai.ragent.ingestion.service;

import com.nageoffer.ai.ragent.rag.controller.request.IntentNodeCreateRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.IntentNodeTreeVO;
import com.nageoffer.ai.ragent.rag.controller.request.IntentNodeUpdateRequest;

import java.util.List;

public interface IntentTreeService {

    /**
     * Loads the whole intent tree (RAG + SYSTEM)
     */
    List<IntentNodeTreeVO> getFullTree();

    /**
     * Creates a node
     */
    String createNode(IntentNodeCreateRequest requestParam);

    /**
     * Updates a node
     */
    void updateNode(String id, IntentNodeUpdateRequest requestParam);

    /**
     * Deletes a node (logical delete)
     */
    void deleteNode(String id);

    /**
     * Enables nodes in batch
     */
    void batchEnableNodes(List<String> ids);

    /**
     * Disables nodes in batch
     */
    void batchDisableNodes(List<String> ids);

    /**
     * Deletes nodes in batch (logical delete)
     */
    void batchDeleteNodes(List<String> ids);
}
