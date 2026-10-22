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

package com.nageoffer.ai.ragent.core.parser.mineru;

import com.nageoffer.ai.ragent.core.parser.DocumentParser;
import com.nageoffer.ai.ragent.core.parser.ParserType;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MinerU document parser (PDF / Word / PPT / Excel). Uses the official "local file batch upload and parse", then polls for the result
 * <p>
 * The local upload path does not depend on any publicly reachable source URL, so it fits an intranet deployment.
 * Parse permits are issued across instances by {@link RPermitExpirableSemaphore}, capping how many tasks MinerU
 * runs at once. Settings are in {@link MinerUProperties}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinerUDocumentParser implements DocumentParser {

    /**
     * Layout-parsing types: PDF / Word / PPT have only the MinerU path, and it serves both profiles
     */
    private static final Set<String> LAYOUT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/x-pdf",
            "application/msword",
            "application/vnd.ms-word",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
    );

    /**
     * Table types are claimed only on the fidelity profile. The default is POI's fast key-value parse; MinerU's cost is paid only when the user picks fidelity
     */
    private static final Set<String> SPREADSHEET_MIME_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel"
    );

    /**
     * options field: file name, written to Provenance.sourceFile
     */
    public static final String OPT_SOURCE_FILE = "sourceFile";

    /**
     * options field: document id, used to name asset keys. A UUID is generated when it is omitted
     */
    public static final String OPT_DOCUMENT_ID = "documentId";

    /**
     * ParsedDocument.metadata field: the batchId MinerU assigned. Use it to look the task up on the MinerU side when debugging
     */
    public static final String META_BATCH_ID = "minerU.batchId";

    /**
     * ParsedDocument.metadata field: zip download URL
     */
    public static final String META_ZIP_URL = "minerU.zipUrl";

    private final MinerUClient minerUClient;
    private final MinerUPollingExecutor pollingExecutor;
    private final MinerUResultUnpacker resultUnpacker;
    private final MinerUProperties properties;
    private final RedissonClient redissonClient;

    @PostConstruct
    void initSemaphore() {
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(properties.getSemaphoreName());
        semaphore.setPermits(properties.getConcurrencyLimit());
        log.info("MinerU 分布式解析限流初始化: semaphoreName={}, maxConcurrent={}",
                properties.getSemaphoreName(), properties.getConcurrencyLimit());
    }

    @Override
    public String getParserType() {
        return ParserType.MINERU.getType();
    }

    @Override
    public Map<ParseProfile, Set<String>> supportedMimeTypes() {
        return Map.of(
                ParseProfile.FAST, LAYOUT_MIME_TYPES,
                ParseProfile.FIDELITY, SPREADSHEET_MIME_TYPES
        );
    }

    @Override
    public ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            throw new ServiceException("MinerU parse input bytes are empty");
        }

        String permitId = null;
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(properties.getSemaphoreName());
        try {
            permitId = semaphore.tryAcquire(
                    properties.getMaxWaitSeconds(),
                    properties.getLeaseSeconds(),
                    TimeUnit.SECONDS
            );
            if (permitId == null) {
                throw new ServiceException("Too many MinerU parse tasks. Please try again later");
            }
            return doParseStructured(content, mimeType, options);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("MinerU parse permit wait was interrupted");
        } finally {
            if (permitId != null) {
                boolean released = semaphore.tryRelease(permitId);
                if (!released) {
                    log.warn("MinerU parse permit already expired or released, permitId={}", permitId);
                }
            }
        }
    }

    private ParsedDocument doParseStructured(byte[] content, String mimeType, Map<String, Object> options) {
        String sourceFile = extractString(options, OPT_SOURCE_FILE, "");
        String documentId = extractString(options, OPT_DOCUMENT_ID, UUID.randomUUID().toString());
        String uploadName = resolveUploadName(sourceFile, mimeType, documentId);

        // 1. Apply for an upload URL. Submit metadata only, with no url
        BatchSubmitRequest request = buildSubmitRequest(uploadName, documentId);
        BatchUploadTicket ticket = minerUClient.requestUpload(request);

        // 2. PUT the source-file bytes straight to MinerU OSS
        minerUClient.uploadFile(ticket.uploadUrl(), content);
        log.info("MinerU 源文件上传完毕 documentId={} batchId={}", documentId, ticket.batchId());

        // 3. Block until done. The upload itself makes MinerU submit the parse; no separate submit call
        MinerUStatus status;
        try {
            status = pollingExecutor
                    .submitAndAwait(ticket.batchId(), Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .get(properties.getTimeoutSeconds() + 30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new ServiceException("MinerU wait timed out (including scheduling buffer) batchId=" + ticket.batchId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("MinerU wait was interrupted batchId=" + ticket.batchId());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new ServiceException("MinerU wait failed batchId=" + ticket.batchId() + ": " + cause.getMessage());
        }

        // 4. Download the zip
        byte[] zipBytes = minerUClient.downloadZip(status.zipUrl());

        // 5. Unpack into a ParsedDocument
        ParsedDocument parsed = resultUnpacker.unpack(zipBytes, sourceFile, documentId);

        // 6. Inject batchId + zipUrl into metadata so a downstream node can persist them for debugging
        Map<String, Object> mergedMeta = new HashMap<>(parsed.metadata() == null ? Map.of() : parsed.metadata());
        mergedMeta.put(META_BATCH_ID, ticket.batchId());
        mergedMeta.put(META_ZIP_URL, status.zipUrl());
        mergedMeta.put("parser", getParserType());
        mergedMeta.put("mimeType", mimeType == null ? "" : mimeType);

        return ParsedDocument.of(parsed.blocks(), mergedMeta);
    }

    /**
     * Computes the file name uploaded to MinerU. It must have an extension, which MinerU uses to detect the format. When the original name is missing, complete it from mimeType
     */
    private String resolveUploadName(String sourceFile, String mimeType, String documentId) {
        if (sourceFile != null && !sourceFile.isBlank()) {
            return sourceFile;
        }
        return "doc-" + documentId + extFromMime(mimeType);
    }

    private BatchSubmitRequest buildSubmitRequest(String fileName, String documentId) {
        return new BatchSubmitRequest(
                fileName,
                documentId,
                properties.isOcr(),
                properties.isEnableTable(),
                properties.isEnableFormula(),
                properties.getLanguage()
        );
    }

    private static String extFromMime(String mimeType) {
        if (mimeType == null) {
            return ".bin";
        }
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("pdf")) return ".pdf";
        if (lower.contains("wordprocessingml")) return ".docx";
        if (lower.contains("msword")) return ".doc";
        if (lower.contains("presentationml")) return ".pptx";
        if (lower.contains("powerpoint")) return ".ppt";
        if (lower.contains("spreadsheetml")) return ".xlsx";
        if (lower.contains("ms-excel") || lower.contains("excel")) return ".xls";
        return ".bin";
    }

    private static String extractString(Map<String, Object> options, String key, String defaultValue) {
        if (options == null) {
            return defaultValue;
        }
        Object v = options.get(key);
        return (v == null || v.toString().isBlank()) ? defaultValue : v.toString();
    }
}
