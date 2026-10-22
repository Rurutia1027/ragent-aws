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

package com.nageoffer.ai.ragent.rag.config;

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.rag.core.storage.ObjectStorageClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Startup initializer for object-storage buckets
 * <p>
 * On startup, create two global buckets so operators do not create them or set permissions by hand:
 * <ul>
 *   <li>Knowledge-base bucket {@code rag.storage.kb-bucket}: private, holds every knowledge-base document, isolated by collectionName directory</li>
 *   <li>Asset bucket {@code rag.storage.asset-bucket}: public read, so images extracted from PDFs can be previewed by the browser anonymously</li>
 * </ul>
 * In a cluster, a Redisson lock ensures creation happens once: check existence, take the lock, double-check, then create the bucket
 * <p>
 * Failure policy: fail fast. If storage or Redis is unavailable, let the exception propagate so the problem shows up at startup
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageInitializer {

    private static final String LOCK_KEY_PREFIX = "ragent:storage:bucket:init:";
    private static final long LOCK_WAIT_SECONDS = 30;

    private final ObjectStorageClient objectStorageClient;
    private final RedissonClient redissonClient;
    private final RagStorageProperties properties;

    @PostConstruct
    public void initBuckets() {
        ensureBucket(properties.getKbBucket(), false);
        ensureBucket(properties.getAssetBucket(), true);
    }

    private void ensureBucket(String bucket, boolean publicRead) {
        ensureBucketExists(bucket);
        if (publicRead) {
            // Idempotently apply public-read: whether newly created or already present, objects in the bucket stay anonymously previewable
            objectStorageClient.setBucketPublicRead(bucket);
            log.info("对象存储桶就绪（公共读）bucket={}", bucket);
        } else {
            log.info("对象存储桶就绪 bucket={}", bucket);
        }
    }

    private void ensureBucketExists(String bucket) {
        if (objectStorageClient.bucketExists(bucket)) {
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + bucket);
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Interrupted while acquiring the distributed lock to initialize the object storage bucket bucket=" + bucket);
        }
        if (!locked) {
            throw new ServiceException("Timed out acquiring the distributed lock to initialize the object storage bucket bucket=" + bucket);
        }

        try {
            if (objectStorageClient.bucketExists(bucket)) {
                return;
            }
            objectStorageClient.createBucket(bucket);
            log.info("对象存储桶创建成功 bucket={}", bucket);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
