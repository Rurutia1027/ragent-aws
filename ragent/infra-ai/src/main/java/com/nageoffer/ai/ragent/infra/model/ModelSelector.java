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
import com.nageoffer.ai.ragent.infra.enums.ModelProvider;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Model selector
 * Builds the candidate list from configuration and the current request
 * <p>
 * The chat group uses tiers: task, then tier, then ordered candidates inside that tier.
 * embedding, rerank, and vlm use the traditional defaultModel plus priority ordering
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelSelector {

    private final AIModelProperties properties;
    private final ModelHealthStore healthStore;

    /**
     * Select chat candidates for the default tier
     * <p>
     * Tier resolution: deep thinking uses deepThinkingTier; otherwise fall back to defaultTier
     */
    public List<ModelTarget> selectChatCandidates(boolean thinking) {
        return selectChatCandidates(thinking, null, null);
    }

    /**
     * Select chat candidates with an explicit tier override
     * <p>
     * A non-null override uses that tier (the call site passes it when it wants a faster or stronger model). Null uses default resolution
     */
    public List<ModelTarget> selectChatCandidates(boolean thinking, Tier override) {
        return selectChatCandidates(thinking, override, null);
    }

    /**
     * Select chat candidates with an explicit tier override, and place the preferred model first
     * <p>
     * Preferred means try that model first, then fall back to the remaining candidates of the resolved tier
     *
     * @param override         tier override; null uses default resolution (deep thinking to deepThinkingTier, otherwise defaultTier)
     * @param preferredModelId preferred model id; null means no preference
     */
    public List<ModelTarget> selectChatCandidates(boolean thinking, Tier override, String preferredModelId) {
        AIModelProperties.ModelGroup group = properties.getChat();
        if (group == null) {
            return List.of();
        }
        String tierName = resolveTierName(group, thinking, override);
        // When the user requests thinking, both routing and the preferred model must drop models that cannot think
        return buildTierTargets(group, tierName, preferredModelId, thinking);
    }

    public List<ModelTarget> selectEmbeddingCandidates() {
        return selectCandidates(properties.getEmbedding());
    }

    public List<ModelTarget> selectRerankCandidates() {
        return selectCandidates(properties.getRerank());
    }

    public List<ModelTarget> selectVlmCandidates() {
        return selectCandidates(properties.getVlm());
    }

    // ==================== chat: tier mechanism ====================

    private String resolveTierName(AIModelProperties.ModelGroup group, boolean thinking, Tier override) {
        if (thinking && StrUtil.isNotBlank(group.getDeepThinkingTier())) {
            return group.getDeepThinkingTier();
        }
        if (override != null) {
            return override.getKey();
        }
        return group.getDefaultTier();
    }

    /**
     * Build ordered candidates for a tier: preferred first, then the tier candidates with duplicates removed, dropping disabled, unhealthy, or unregistered ones
     * <p>
     * When requireThinking is true, also drop candidates whose supportsThinking is not true, including the preferred model,
     * so a thinking request is not routed to a model that cannot think. The matched tier timeout is copied onto each target
     *
     * @param requireThinking whether candidates must support a thinking chain
     */
    private List<ModelTarget> buildTierTargets(AIModelProperties.ModelGroup group, String tierName,
                                                  String preferredModelId, boolean requireThinking) {
        Map<String, AIModelProperties.ModelCandidate> registry = buildRegistry(group.getCandidates());

        List<String> orderedIds = new ArrayList<>();
        if (StrUtil.isNotBlank(preferredModelId)) {
            AIModelProperties.ModelCandidate preferred = registry.get(preferredModelId);
            if (preferred == null) {
                log.warn("Chat preferred 模型未在注册表登记，忽略并回退档位候选: preferredModelId={}", preferredModelId);
            } else if (requireThinking && !supportsThinking(preferred)) {
                log.warn("Chat preferred 模型不支持思考，思考请求下忽略: preferredModelId={}", preferredModelId);
            } else {
                orderedIds.add(preferredModelId);
            }
        }

        AIModelProperties.TierConfig tier = group.getTiers() == null ? null : group.getTiers().get(tierName);
        Long timeoutMs = tier == null ? null : tier.getTimeoutMs();
        if (tier == null) {
            log.warn("Chat 档位配置缺失: tier={}", tierName);
        } else {
            for (String id : tier.getCandidates()) {
                if (!orderedIds.contains(id)) {
                    orderedIds.add(id);
                }
            }
        }

        Map<String, AIModelProperties.ProviderConfig> providers = properties.getProviders();
        List<ModelTarget> targets = new ArrayList<>();
        for (String id : orderedIds) {
            AIModelProperties.ModelCandidate candidate = registry.get(id);
            if (candidate == null) {
                log.warn("Chat 档位候选 id 未在注册表登记: id={}, tier={}", id, tierName);
                continue;
            }
            if (Boolean.FALSE.equals(candidate.getEnabled())) {
                continue;
            }
            if (requireThinking && !supportsThinking(candidate)) {
                continue;
            }
            ModelTarget target = buildModelTarget(candidate, providers, timeoutMs);
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    private boolean supportsThinking(AIModelProperties.ModelCandidate candidate) {
        return Boolean.TRUE.equals(candidate.getSupportsThinking());
    }

    private Map<String, AIModelProperties.ModelCandidate> buildRegistry(List<AIModelProperties.ModelCandidate> candidates) {
        Map<String, AIModelProperties.ModelCandidate> registry = new LinkedHashMap<>();
        if (candidates == null) {
            return registry;
        }
        for (AIModelProperties.ModelCandidate candidate : candidates) {
            if (candidate != null) {
                registry.put(resolveId(candidate), candidate);
            }
        }
        return registry;
    }

    // ==================== embedding/rerank/vlm: defaultModel + priority ====================

    private List<ModelTarget> selectCandidates(AIModelProperties.ModelGroup group) {
        if (group == null || group.getCandidates() == null) {
            return List.of();
        }
        List<AIModelProperties.ModelCandidate> orderedCandidates =
                filterAndSortCandidates(group.getCandidates(), group.getDefaultModel());
        return buildAvailableTargets(orderedCandidates);
    }

    /**
     * Filter and sort candidates: the preferred model first, then by priority and id
     */
    private List<AIModelProperties.ModelCandidate> filterAndSortCandidates(List<AIModelProperties.ModelCandidate> candidates,
                                                                           String firstChoiceModelId) {
        return candidates.stream()
                .filter(c -> c != null && !Boolean.FALSE.equals(c.getEnabled()))
                .sorted(Comparator
                        .comparing((AIModelProperties.ModelCandidate c) ->
                                !Objects.equals(resolveId(c), firstChoiceModelId))
                        .thenComparing(AIModelProperties.ModelCandidate::getPriority,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AIModelProperties.ModelCandidate::getId,
                                Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private List<ModelTarget> buildAvailableTargets(List<AIModelProperties.ModelCandidate> candidates) {
        Map<String, AIModelProperties.ProviderConfig> providers = properties.getProviders();

        // embedding, rerank, and vlm have no tier budget; timeouts use the HTTP client default
        return candidates.stream()
                .map(candidate -> buildModelTarget(candidate, providers, null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== Shared ====================

    private ModelTarget buildModelTarget(AIModelProperties.ModelCandidate candidate,
                                         Map<String, AIModelProperties.ProviderConfig> providers,
                                         Long timeoutMs) {
        String modelId = resolveId(candidate);

        if (healthStore.isUnavailable(modelId)) {
            return null;
        }

        AIModelProperties.ProviderConfig provider = providers.get(candidate.getProvider());
        if (provider == null && !ModelProvider.NOOP.matches(candidate.getProvider())) {
            log.warn("Provider配置缺失: provider={}, modelId={}", candidate.getProvider(), modelId);
            return null;
        }

        return new ModelTarget(modelId, candidate, provider, timeoutMs);
    }

    private String resolveId(AIModelProperties.ModelCandidate candidate) {
        if (StrUtil.isNotBlank(candidate.getId())) {
            return candidate.getId();
        }
        return String.format("%s::%s",
                Objects.toString(candidate.getProvider(), "unknown"),
                Objects.toString(candidate.getModel(), "unknown"));
    }
}
