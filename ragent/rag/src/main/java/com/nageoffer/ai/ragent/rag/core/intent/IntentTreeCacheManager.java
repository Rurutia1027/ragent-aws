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

package com.nageoffer.ai.ragent.rag.core.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Intent-tree cache manager.
 * Caches the intent tree in Redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentTreeCacheManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis cache key.
     */
    private static final String INTENT_TREE_CACHE_KEY = "ragent:intent:tree";

    /**
     * Cache TTL: 7 days.
     */
    private static final long CACHE_EXPIRE_DAYS = 7;

    /**
     * Load the intent tree from Redis.
     *
     * @return root nodes, or null when the cache is missing
     */
    public List<IntentNode> getIntentTreeFromCache() {
        try {
            String cacheJson = stringRedisTemplate.opsForValue().get(INTENT_TREE_CACHE_KEY);
            if (cacheJson == null) {
                log.info("意图树缓存不存在，需要从数据库加载");
                return null;
            }

            return objectMapper.readValue(
                    cacheJson,
                    new TypeReference<>() {
                    }
            );
        } catch (Exception e) {
            log.error("从Redis读取意图树缓存失败", e);
            return null;
        }
    }

    /**
     * Save the intent tree to Redis.
     *
     * @param roots intent-tree root nodes
     */
    public void saveIntentTreeToCache(List<IntentNode> roots) {
        try {
            String cacheJson = objectMapper.writeValueAsString(roots);
            stringRedisTemplate.opsForValue().set(
                    INTENT_TREE_CACHE_KEY,
                    cacheJson,
                    CACHE_EXPIRE_DAYS,
                    TimeUnit.DAYS
            );
            log.info("意图树已保存到Redis缓存，根节点数: {}", roots.size());
        } catch (Exception e) {
            log.error("保存意图树到Redis缓存失败", e);
        }
    }

    /**
     * Clear the intent-tree cache.
     * Call this when intent nodes are created, updated, or deleted.
     */
    public void clearIntentTreeCache() {
        Boolean deleted = stringRedisTemplate.delete(INTENT_TREE_CACHE_KEY);
        if (deleted) {
            log.info("意图树缓存已清除，Key: {}", INTENT_TREE_CACHE_KEY);
        } else {
            log.info("意图树缓存不存在，无需清除");
        }
    }

    /**
     * Whether the cache exists.
     *
     * @return true when the cache exists, false otherwise
     */
    public boolean isCacheExists() {
        try {
            return stringRedisTemplate.hasKey(INTENT_TREE_CACHE_KEY);
        } catch (Exception e) {
            log.error("检查意图树缓存是否存在失败", e);
            return false;
        }
    }
}
