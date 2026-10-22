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

package com.nageoffer.ai.ragent.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeChunkVO;

import java.util.List;

/**
 * Knowledge-base chunk service
 */
public interface KnowledgeChunkService {

    /**
     * Pages chunks of a document
     *
     * @param docId        document id
     * @param requestParam page query
     * @return chunk page
     */
    IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam);

    /**
     * Adds a chunk to a document
     *
     * @param docId        document id
     * @param requestParam create-chunk request
     * @return the created chunk view
     */
    KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest requestParam);



    /**
     * Updates one chunk of a document
     *
     * @param docId        document id
     * @param chunkId      chunk id
     * @param requestParam update-chunk request
     */
    void update(String docId, String chunkId, KnowledgeChunkUpdateRequest requestParam);

    /**
     * Deletes one chunk of a document
     *
     * @param docId   document id
     * @param chunkId chunk id
     */
    void delete(String docId, String chunkId);

    /**
     * Enables or disables one chunk
     *
     * @param docId   document id
     * @param chunkId chunk id
     * @param enabled whether to enable it
     */
    void enableChunk(String docId, String chunkId, boolean enabled);

    /**
     * Enables or disables document chunks in batch
     *
     * @param docId        document id
     * @param requestParam batch request (empty means every chunk)
     * @param enabled      true to enable, false to disable
     */
    void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled);

    /**
     * Updates the enabled flag of every chunk of a document
     *
     * @param docId   document id
     * @param kbId    knowledge-base id (for logs, to avoid a repeated query)
     * @param enabled whether to enable them
     */
    void updateEnabledByDocId(String docId, String kbId, boolean enabled);

    /**
     * Re-embeds every stored chunk of a document. Used to rebuild vectors when the document is enabled
     * <p>
     * Does not go through a controller view. Embedding text is internal to the index and is not exposed
     *
     * @param docId  document id
     * @param target vector target (model + dimension)
     * @return embedded chunks, ascending by chunkIndex
     */
    List<EmbeddedChunk> embedPersistedChunks(String docId, VectorTarget target);

    /**
     * Deletes every chunk of a document
     *
     * @param docId document id
     */
    void deleteByDocId(String docId);
}
