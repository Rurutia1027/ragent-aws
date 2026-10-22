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

package com.nageoffer.ai.ragent.rag.core.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Skill cache manager.
 * Caches the full list of enabled skills. Every turn reads it, so it should not hit the database each time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSkillCacheManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY = "ragent:agent:enabled-skills:v1";

    private static final long CACHE_EXPIRE_HOURS = 1;

    /**
     * @return enabled skills, or null when the cache is missing; an empty list is a valid result and means no skill is configured
     */
    public List<AgentSkill> getFromCache() {
        try {
            String cacheJson = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (cacheJson == null) {
                return null;
            }
            return objectMapper.readValue(cacheJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("从 Redis 读取技能缓存失败", e);
            return null;
        }
    }

    public void saveToCache(List<AgentSkill> skills) {
        try {
            String cacheJson = objectMapper.writeValueAsString(skills);
            stringRedisTemplate.opsForValue().set(CACHE_KEY, cacheJson, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存技能到 Redis 缓存失败", e);
        }
    }

    /**
     * Must be called after any skill write, or the change stays invisible until the cache expires.
     */
    public void clearCache() {
        try {
            stringRedisTemplate.delete(CACHE_KEY);
            log.info("技能缓存已清除");
        } catch (Exception e) {
            log.error("清除技能缓存失败", e);
        }
    }
}
