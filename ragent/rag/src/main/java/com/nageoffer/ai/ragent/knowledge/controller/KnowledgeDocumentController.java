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

package com.nageoffer.ai.ragent.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.IngestionSpecSchemaVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeDocumentVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeDocumentService;
import com.nageoffer.ai.ragent.knowledge.support.IngestionSpecSchemaProvider;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Knowledge-base document controller.
 * Upload, chunk, delete, query, and enable or disable documents
 */
@RestController
@RequiredArgsConstructor
@Validated
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService documentService;
    private final FileStorageService fileStorageService;
    private final IngestionSpecSchemaProvider ingestionSpecSchemaProvider;

    private static final Map<String, String> CONTENT_TYPE_MAP = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("md", "text/markdown"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv;charset=utf-8"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("svg", "image/svg+xml")
    );

    /**
     * Form schema for the ingestion config. It describes the {@code ingestionSpec} field on the upload and update APIs
     */
    @GetMapping("/knowledge-base/docs/ingestion-spec-schema")
    public Result<IngestionSpecSchemaVO> getIngestionSpecSchema() {
        return Results.success(ingestionSpecSchemaProvider.describe());
    }

    /**
     * Uploads a document: stores the row and the file, and returns the document id
     */
    @PostMapping(value = "/knowledge-base/{kb-id}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentVO> upload(@PathVariable("kb-id") String kbId,
                                              @RequestPart(value = "file", required = false) MultipartFile file,
                                              @ModelAttribute KnowledgeDocumentUploadRequest requestParam) {
        return Results.success(documentService.upload(kbId, requestParam, file));
    }

    /**
     * Starts chunking: extract text, split, embed, and write the vector store
     */
    @PostMapping("/knowledge-base/docs/{doc-id}/chunk")
    public Result<Void> startChunk(@PathVariable(value = "doc-id") String docId) {
        documentService.startChunk(docId);
        return Results.success();
    }

    /**
     * Deletes a document: logical delete. Optionally also deletes every chunk of that document in the vector store
     */
    @DeleteMapping("/knowledge-base/docs/{doc-id}")
    public Result<Void> delete(@PathVariable(value = "doc-id") String docId) {
        documentService.delete(docId);
        return Results.success();
    }

    /**
     * Gets document detail
     */
    @GetMapping("/knowledge-base/docs/{docId}")
    public Result<KnowledgeDocumentVO> get(@PathVariable String docId) {
        return Results.success(documentService.get(docId));
    }

    /**
     * Updates document info
     */
    @PutMapping("/knowledge-base/docs/{docId}")
    public Result<Void> update(@PathVariable String docId,
                               @RequestBody KnowledgeDocumentUpdateRequest requestParam) {
        documentService.update(docId, requestParam);
        return Results.success();
    }

    /**
     * Pages documents (filter by status or keyword)
     */
    @GetMapping("/knowledge-base/{kb-id}/docs")
    public Result<IPage<KnowledgeDocumentVO>> page(@PathVariable(value = "kb-id") String kbId,
                                                   KnowledgeDocumentPageRequest requestParam) {
        return Results.success(documentService.page(kbId, requestParam));
    }

    /**
     * Searches documents (global retrieval suggestions)
     */
    @GetMapping("/knowledge-base/docs/search")
    public Result<List<KnowledgeDocumentSearchVO>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "limit", defaultValue = "8") int limit) {
        return Results.success(documentService.search(keyword, limit));
    }

    /**
     * Enables or disables a document
     */
    @PatchMapping("/knowledge-base/docs/{docId}/enable")
    public Result<Void> enable(@PathVariable String docId,
                               @RequestParam("value") boolean enabled) {
        documentService.enable(docId, enabled);
        return Results.success();
    }

    /**
     * Lists document chunk logs
     */
    @GetMapping("/knowledge-base/docs/{docId}/chunk-logs")
    public Result<IPage<KnowledgeDocumentChunkLogVO>> getChunkLogs(@PathVariable String docId,
                                                                   Page<KnowledgeDocumentChunkLogVO> page) {
        return Results.success(documentService.getChunkLogs(docId, page));
    }

    /**
     * Previews markdown document content
     */
    @GetMapping("/knowledge-base/docs/{docId}/preview")
    public Result<String> preview(@PathVariable String docId) {
        return Results.success(documentService.preview(docId));
    }

    /**
     * Gets the source file (so the browser can render PDF, images, and other formats it supports natively)
     */
    @GetMapping("/knowledge-base/docs/{docId}/file")
    public void file(@PathVariable String docId, HttpServletResponse response) throws Exception {
        var doc = documentService.get(docId);
        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        String contentType = CONTENT_TYPE_MAP.getOrDefault(fileType, "application/octet-stream");
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", ContentDisposition.inline()
                .filename(doc.getDocName(), StandardCharsets.UTF_8)
                .build()
                .toString());
        try (InputStream in = fileStorageService.openStream(doc.getFileUrl())) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }
}
