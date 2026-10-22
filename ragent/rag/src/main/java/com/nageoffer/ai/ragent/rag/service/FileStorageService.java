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

package com.nageoffer.ai.ragent.rag.service;

import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * File storage service: a backend-agnostic facade
 * <p>
 * Every knowledge-base document shares one global bucket, partitioned by {@code namespace} (= knowledge-base collectionName) as a directory (key prefix).
 * Multimodal assets go to the public-read asset bucket. Underneath, {@link com.nageoffer.ai.ragent.rag.core.storage.ObjectStorageClient}
 * switches between S3-compatible storage and Alibaba Cloud OSS. Business code depends only on this interface
 * <p>
 * Stored references ({@link StoredFileDTO#getUrl()} / document file_url) keep only the bare key (for example {@code {namespace}/{uuid}.ext}):
 * the bucket is a deployment constant and is not written into data. Reads and writes pick the bucket by operation (documents -> knowledge-base bucket, assets -> asset bucket)
 */
public interface FileStorageService {

    /**
     * Upload a knowledge-base document (streaming, low memory)
     * <p>
     * Writes the global knowledge-base bucket. Object key = {@code {namespace}/{uuid}.{ext}}
     * The SDK does not retry automatically; the caller must retry on failure
     *
     * @param namespace knowledge-base namespace (collectionName)
     */
    StoredFileDTO upload(String namespace, MultipartFile file);

    /**
     * Upload a knowledge-base document (streaming, low memory)
     *
     * @param namespace knowledge-base namespace (collectionName)
     */
    StoredFileDTO upload(String namespace, InputStream content, long size, String originalFilename, String contentType);

    /**
     * Upload a knowledge-base document (streaming, low memory)
     *
     * @param namespace knowledge-base namespace (collectionName)
     */
    StoredFileDTO upload(String namespace, byte[] content, String originalFilename, String contentType);

    /**
     * Upload a knowledge-base document (native SDK, with automatic retry)
     * <p>
     * Retries automatically, at the cost of possibly buffering the payload on the heap. Suited to small files or reliability-sensitive calls
     *
     * @param namespace knowledge-base namespace (collectionName)
     */
    StoredFileDTO reliableUpload(String namespace, InputStream content, long size, String originalFilename, String contentType);

    /**
     * Upload a multimodal asset (public read)
     * <p>
     * Writes the asset bucket, so {@link #getPublicUrl} can turn it into a preview URL the browser can open anonymously
     */
    StoredFileDTO uploadAsset(byte[] content, String originalFilename, String contentType);

    /**
     * Open an input stream for one of our documents (knowledge-base bucket)
     *
     * @param key bare object key (for example {@code {namespace}/{uuid}.ext})
     */
    InputStream openStream(String key);

    /**
     * Delete one of our documents (knowledge-base bucket)
     *
     * @param key bare object key
     */
    void deleteByUrl(String key);

    /**
     * Turn a bare asset key into a public preview URL the browser can open anonymously (asset bucket)
     * <p>
     * The asset bucket must already be public-read. Only for objects that may be previewed publicly, such as multimodal assets
     *
     * @param key bare asset object key
     * @return public HTTP URL
     */
    String getPublicUrl(String key);

    /**
     * Create a knowledge-base space (idempotent): make a directory in the global knowledge-base bucket (write a {@code {namespace}/} marker object)
     *
     * @param namespace knowledge-base namespace (collectionName)
     */
    void createKnowledgeSpace(String namespace);

    /**
     * Delete a knowledge-base space (idempotent): remove every object under the {@code {namespace}/} prefix in the global knowledge-base bucket
     * <p>
     * Deletes only that knowledge-base directory, never the bucket
     *
     * @param namespace knowledge-base namespace (collectionName)
     */
    void deleteKnowledgeSpace(String namespace);
}
