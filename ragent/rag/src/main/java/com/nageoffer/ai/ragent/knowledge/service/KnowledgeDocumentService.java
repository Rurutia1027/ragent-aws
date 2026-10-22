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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeDocumentVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Knowledge-base document service
 */
public interface KnowledgeDocumentService {

    /**
     * Uploads a document
     *
     * @param kbId         knowledge-base id
     * @param requestParam request
     * @param file         file to upload
     * @return knowledge-base document view
     */
    KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file);

    /**
     * Starts document chunking (validates status, sends an MQ message, and returns immediately)
     *
     * @param docId document id
     */
    void startChunk(String docId);

    /**
     * Runs document chunking (called by the MQ consumer).
     * Acquire the distributed lock, clear historical chunks and vectors, then run the full chunk flow
     *
     * @param docId document id
     */
    void executeChunk(String docId);

    /**
     * Deletes a document
     *
     * @param docId document id
     */
    void delete(String docId);

    /**
     * Gets document detail
     *
     * @param docId document id
     * @return knowledge-base document view
     */
    KnowledgeDocumentVO get(String docId);

    /**
     * Updates document info
     *
     * @param docId        document id
     * @param requestParam update request
     */
    void update(String docId, KnowledgeDocumentUpdateRequest requestParam);

    /**
     * Pages documents
     *
     * @param kbId         knowledge-base id
     * @param requestParam filter
     * @return document page
     */
    IPage<KnowledgeDocumentVO> page(String kbId, KnowledgeDocumentPageRequest requestParam);

    /**
     * Enables or disables a document
     *
     * @param docId   document id
     * @param enabled whether to enable it
     */
    void enable(String docId, boolean enabled);

    /**
     * Searches documents (global retrieval suggestions)
     *
     * @param keyword keyword
     * @param limit   max number to return
     * @return document list
     */
    List<KnowledgeDocumentSearchVO> search(String keyword, int limit);

    /**
     * Lists document chunk logs
     *
     * @param docId document id
     * @param page  page parameters
     * @return chunk-log page
     */
    IPage<KnowledgeDocumentChunkLogVO> getChunkLogs(String docId, Page<KnowledgeDocumentChunkLogVO> page);

    /**
     * Previews markdown document content
     *
     * @param docId document id
     * @return raw markdown text
     */
    String preview(String docId);
}
