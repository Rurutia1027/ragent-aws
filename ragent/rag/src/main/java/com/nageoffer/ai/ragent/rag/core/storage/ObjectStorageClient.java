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

package com.nageoffer.ai.ragent.rag.core.storage;

import java.io.InputStream;

/**
 * Object-storage SPI: only raw (bucket, key) operations. It does not know about namespace, key assembly, or business DTOs
 * <p>
 * One implementation each for S3-compatible storage (rustfs / minio) and Alibaba Cloud OSS. {@code rag.storage.type} registers exactly one via {@code @ConditionalOnProperty}.
 * Namespace and key assembly, bucket ownership, and content-type detection are backend-neutral and live in
 * {@link com.nageoffer.ai.ragent.rag.service.impl.DefaultFileStorageService}. This interface only talks to the concrete store
 */
public interface ObjectStorageClient {

    /**
     * Streaming upload (low memory)
     * <p>
     * Each backend uses the cheapest way it has: S3 uses a presigned URL plus HttpURLConnection for a zero-heap stream;
     * OSS uses the SDK putObject with contentLength and streams in blocks. Automatic retry is not guaranteed. The caller retries on failure
     *
     * @param bucket      bucket name
     * @param key         object key
     * @param content     content stream
     * @param size        content size in bytes
     * @param contentType content type already detected
     */
    void streamPut(String bucket, String key, InputStream content, long size, String contentType);

    /**
     * Reliable upload (native SDK, with automatic retry)
     * <p>
     * Network jitter or timeout is retried. The cost is that the payload may be buffered on the heap. Use it for small files or when reliability matters
     */
    void reliablePut(String bucket, String key, InputStream content, long size, String contentType);

    /**
     * Open a read stream for the object. The caller closes it
     */
    InputStream getObject(String bucket, String key);

    /**
     * Delete one object (idempotent)
     */
    void deleteObject(String bucket, String key);

    /**
     * List by prefix, page by page, and delete in batches (idempotent). Used to delete a knowledge-base directory
     *
     * @param bucket bucket name
     * @param prefix key prefix, such as {@code {collectionName}/}
     */
    void deleteByPrefix(String bucket, String prefix);

    /**
     * Whether the object exists
     */
    boolean objectExists(String bucket, String key);

    /**
     * Whether the bucket exists
     */
    boolean bucketExists(String bucket);

    /**
     * Create the bucket (idempotent: already exists counts as success)
     */
    void createBucket(String bucket);

    /**
     * Apply a public-read policy to the bucket (idempotent), so objects in it can be previewed anonymously in a browser
     */
    void setBucketPublicRead(String bucket);

    /**
     * Build a public URL a browser can open directly
     * <p>
     * S3/rustfs is path-style ({@code {base}/{bucket}/{key}}); OSS is virtual-hosted ({@code {bucketBase}/{key}})
     */
    String buildPublicUrl(String bucket, String key);
}
