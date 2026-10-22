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
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceId;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceSpec;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreAdmin;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Startup initializer for the shared vector space
 * <p>
 * Symmetric with {@link StorageInitializer}: on startup, idempotently ensure the shared vector space exists, so the first knowledge base or first ingest does not depend on lazy init
 * Backend-agnostic: Milvus creates the shared collection; PG relies on migration scripts to create the table, so this is a no-op there. {@link VectorStoreAdmin} hides the difference
 * In a cluster, a Redisson lock ensures it is created once: check existence, take the lock, double-check, then create
 * <p>
 * Failure policy: fail fast. If the vector backend or Redis is unavailable, let the exception propagate so the problem shows up at startup
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSpaceInitializer {

    private static final String LOCK_KEY_PREFIX = "ragent:vector:space:init:";
    private static final long LOCK_WAIT_SECONDS = 30;

    private final VectorStoreAdmin vectorStoreAdmin;
    private final RAGDefaultProperties ragDefaultProperties;
    private final RedissonClient redissonClient;

    @PostConstruct
    public void initVectorSpace() {
        String collectionName = ragDefaultProperties.getCollectionName();
        ensureVectorSpace(collectionName);
        log.info("向量共享空间就绪 collection={}", collectionName);
    }

    private void ensureVectorSpace(String collectionName) {
        VectorSpaceId spaceId = VectorSpaceId.builder().logicalName(collectionName).build();
        if (vectorStoreAdmin.vectorSpaceExists(spaceId)) {
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + collectionName);
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Interrupted while acquiring the distributed lock to initialize the shared vector space collection=" + collectionName);
        }
        if (!locked) {
            throw new ServiceException("Timed out acquiring the distributed lock to initialize the shared vector space collection collection=" + collectionName);
        }

        try {
            if (vectorStoreAdmin.vectorSpaceExists(spaceId)) {
                return;
            }
            vectorStoreAdmin.ensureVectorSpace(VectorSpaceSpec.builder()
                    .spaceId(spaceId)
                    .remark("RAG 向量共享存储")
                    .build());
            log.info("向量共享空间创建成功 collection={}", collectionName);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
