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

package com.nageoffer.ai.ragent.infra.model;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Startup validator for chat tier configuration
 * <p>
 * Tier correctness depends on consistent references among tiers, escalation, and the candidates registry.
 * If those errors surface only at runtime they degrade silently to a "missing tier" log. Fail fast at startup:
 * structural errors block startup, and soft issues (a deep-tier candidate that does not declare thinking support) only warn
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTierConfigValidator implements InitializingBean {

    private final AIModelProperties properties;

    @Override
    public void afterPropertiesSet() {
        AIModelProperties.ModelGroup group = properties.getChat();
        if (group == null) {
            return;
        }

        List<String> errors = new ArrayList<>();
        Map<String, AIModelProperties.ModelCandidate> registry = buildRegistry(group, errors);
        Set<String> registryIds = registry.keySet();
        Map<String, AIModelProperties.TierConfig> tiers = group.getTiers();

        if (tiers == null || tiers.isEmpty()) {
            errors.add("ai.chat.tiers 未配置");
        } else {
            validateTierRef(group.getDefaultTier(), "default-tier", tiers, errors);
            validateTierRef(group.getDeepThinkingTier(), "deep-thinking-tier", tiers, errors);
            validateTierCandidates(tiers, registryIds, errors);
            validateTierEnumCoverage(tiers, errors);
            validateDeepThinkingCandidates(group, tiers, registry, errors);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Chat tier configuration validation failed:\n - " + String.join("\n - ", errors));
        }

        warnDeepThinkingSupport(group, tiers, registry);
        log.info("chat 档位配置校验通过: tiers={}", tiers.keySet());
    }

    /**
     * Build the candidate registry (id to candidate) and require unique ids
     */
    private Map<String, AIModelProperties.ModelCandidate> buildRegistry(AIModelProperties.ModelGroup group, List<String> errors) {
        Map<String, AIModelProperties.ModelCandidate> registry = new LinkedHashMap<>();
        List<AIModelProperties.ModelCandidate> candidates = group.getCandidates();
        if (candidates == null) {
            return registry;
        }
        for (AIModelProperties.ModelCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String id = resolveId(candidate);
            if (registry.putIfAbsent(id, candidate) != null) {
                errors.add("chat candidates 存在重复 id: " + id);
            }
        }
        return registry;
    }

    private void validateTierRef(String tierName, String label,
                                 Map<String, AIModelProperties.TierConfig> tiers, List<String> errors) {
        if (StrUtil.isBlank(tierName)) {
            errors.add(label + " 未配置");
        } else if (!tiers.containsKey(tierName)) {
            errors.add(label + " 引用了不存在的档位: " + tierName);
        }
    }

    private void validateTierCandidates(Map<String, AIModelProperties.TierConfig> tiers,
                                        Set<String> registryIds, List<String> errors) {
        for (Map.Entry<String, AIModelProperties.TierConfig> entry : tiers.entrySet()) {
            String tierName = entry.getKey();
            AIModelProperties.TierConfig tier = entry.getValue();
            Long timeoutMs = tier == null ? null : tier.getTimeoutMs();
            if (timeoutMs == null) {
                errors.add("档位 " + tierName + " 未配置 timeout-ms（必填：流式=首包 TTFT 预算，同步=整段调用上限）");
            } else if (timeoutMs <= 0) {
                errors.add("档位 " + tierName + " 的 timeout-ms 必须为正数: " + timeoutMs);
            }
            List<String> candidates = tier == null ? null : tier.getCandidates();
            if (candidates == null || candidates.isEmpty()) {
                errors.add("档位 " + tierName + " 的候选列表为空");
                continue;
            }
            for (String id : candidates) {
                if (!registryIds.contains(id)) {
                    errors.add("档位 " + tierName + " 引用了未在 candidates 注册表登记的 id: " + id);
                }
            }
        }
    }

    /**
     * Business code references Tier values directly (for example Tier.FAST). Every enum key must have a matching tier,
     * or an override at the call site degrades silently to "missing tier" at runtime
     */
    private void validateTierEnumCoverage(Map<String, AIModelProperties.TierConfig> tiers, List<String> errors) {
        for (Tier tier : Tier.values()) {
            if (!tiers.containsKey(tier.getKey())) {
                errors.add("Tier 枚举 " + tier.name() + " 对应的档位未在 ai.chat.tiers 配置: " + tier.getKey());
            }
        }
    }

    /**
     * The deep-thinking tier must have at least one candidate that is registered, enabled, and thinking-capable.
     * Otherwise a thinking request filters to an empty list via supportsThinking and fails at runtime (hard check)
     */
    private void validateDeepThinkingCandidates(AIModelProperties.ModelGroup group,
                                                Map<String, AIModelProperties.TierConfig> tiers,
                                                Map<String, AIModelProperties.ModelCandidate> registry,
                                                List<String> errors) {
        String deepTierName = group.getDeepThinkingTier();
        if (StrUtil.isBlank(deepTierName)) {
            return;
        }
        AIModelProperties.TierConfig deep = tiers.get(deepTierName);
        if (deep == null || deep.getCandidates() == null) {
            return;
        }
        boolean hasThinkingCandidate = deep.getCandidates().stream().anyMatch(id -> {
            AIModelProperties.ModelCandidate candidate = registry.get(id);
            return candidate != null
                    && !Boolean.FALSE.equals(candidate.getEnabled())
                    && Boolean.TRUE.equals(candidate.getSupportsThinking());
        });
        if (!hasThinkingCandidate) {
            errors.add("deep-thinking-tier " + deepTierName + " 无任何已启用且支持思考的候选");
        }
    }

    /**
     * Soft check: warn one by one when a deep-thinking-tier candidate does not declare supportsThinking. Does not block startup.
     * The hard failure when no candidate supports thinking belongs to validateDeepThinkingCandidates
     */
    private void warnDeepThinkingSupport(AIModelProperties.ModelGroup group,
                                         Map<String, AIModelProperties.TierConfig> tiers,
                                         Map<String, AIModelProperties.ModelCandidate> registry) {
        if (tiers == null || StrUtil.isBlank(group.getDeepThinkingTier())) {
            return;
        }
        AIModelProperties.TierConfig deep = tiers.get(group.getDeepThinkingTier());
        if (deep == null || deep.getCandidates() == null) {
            return;
        }
        for (String id : deep.getCandidates()) {
            AIModelProperties.ModelCandidate candidate = registry.get(id);
            if (candidate != null && !Boolean.TRUE.equals(candidate.getSupportsThinking())) {
                log.warn("deep-thinking-tier 候选未声明 supports-thinking，思考请求下将被过滤: id={}", id);
            }
        }
    }

    /**
     * Same id resolution as ModelSelector: an explicit id wins, otherwise fall back to provider::model
     */
    private String resolveId(AIModelProperties.ModelCandidate candidate) {
        if (StrUtil.isNotBlank(candidate.getId())) {
            return candidate.getId();
        }
        return String.format("%s::%s",
                Objects.toString(candidate.getProvider(), "unknown"),
                Objects.toString(candidate.getModel(), "unknown"));
    }
}
