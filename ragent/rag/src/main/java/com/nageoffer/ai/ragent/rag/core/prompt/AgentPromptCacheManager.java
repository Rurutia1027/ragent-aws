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

package com.nageoffer.ai.ragent.rag.core.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent prompt cache manager.
 * Caches the active agent's slots after custom prompts are overlaid. A hit can be used directly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPromptCacheManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Bump the version when the prompt-slot set changes, so an old cache is not missing a new slot.
     */
    private static final String CACHE_KEY = "ragent:agent:resolved-prompts:v2";

    private static final long CACHE_EXPIRE_HOURS = 1;

    /**
     * @return slot-to-prompt map, or null when the cache is missing
     */
    public Map<String, String> getFromCache() {
        try {
            String cacheJson = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (cacheJson == null) {
                return null;
            }
            return objectMapper.readValue(cacheJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("从 Redis 读取智能体提示词缓存失败", e);
            return null;
        }
    }

    public void saveToCache(Map<String, String> prompts) {
        try {
            String cacheJson = objectMapper.writeValueAsString(prompts);
            stringRedisTemplate.opsForValue().set(CACHE_KEY, cacheJson, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存智能体提示词到 Redis 缓存失败", e);
        }
    }

    /**
     * Must be called after any agent or slot write, or the change stays invisible until the cache expires.
     */
    public void clearCache() {
        try {
            stringRedisTemplate.delete(CACHE_KEY);
            log.info("智能体提示词缓存已清除");
        } catch (Exception e) {
            log.error("清除智能体提示词缓存失败", e);
        }
    }
}
