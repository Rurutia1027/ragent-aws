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

package com.nageoffer.ai.ragent.rag.core.guidance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.rag.config.GuidanceProperties;
import com.nageoffer.ai.ragent.rag.constant.RAGConstant;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNodeRegistry;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScoreFilters;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Intent clarification decision.
 * Intent-tree depth is user-configured, so look for same-name forks only along the root-to-leaf node path, then let the LLM confirm that the user really must choose.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentGuidanceService {

    /**
     * Minimum name length for same-name comparison, so a one-character name does not mark unrelated paths as conflicts.
     */
    private static final int MIN_COMPARABLE_NAME_LENGTH = 2;

    private final GuidanceProperties guidanceProperties;
    private final IntentNodeRegistry intentNodeRegistry;
    private final PromptTemplateLoader promptTemplateLoader;
    private final AmbiguityLLMChecker ambiguityLLMChecker;

    @RagTraceNode(name = "guidance-detect", type = "GUIDANCE")
    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        if (!Boolean.TRUE.equals(guidanceProperties.getEnabled())) {
            return GuidanceDecision.none();
        }

        AmbiguityGroup group = findAmbiguityGroup(question, subIntents);
        if (group == null || CollUtil.isEmpty(group.ranked())) {
            return GuidanceDecision.none();
        }

        String prompt = buildPrompt(group.topicName(), group.ranked());
        return GuidanceDecision.prompt(prompt);
    }

    private AmbiguityGroup findAmbiguityGroup(String question, List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents) || subIntents.size() != 1) {
            return null;
        }

        List<NodeScore> ranked = rankCandidates(filterCandidates(subIntents.get(0).nodeScores()));
        if (ranked.size() < 2) {
            return null;
        }

        PathConflict conflict = collectPathConflicts(question, ranked);
        if (conflict == null) {
            return null;
        }

        if (!ambiguityLLMChecker.checkAmbiguity(question, conflict.ranked())) {
            log.info("LLM 判定候选路径不构成歧义, 跳过澄清, question={}", question);
            return null;
        }

        return new AmbiguityGroup(conflict.topicName(), trimRankedOptions(conflict.ranked()));
    }

    private List<NodeScore> filterCandidates(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores)) {
            return List.of();
        }
        return NodeScoreFilters.kb(scores, RAGConstant.INTENT_MIN_SCORE);
    }

    /**
     * Deduplicate by node and sort by score descending. When the same node is hit more than once, keep the higher score.
     */
    private List<NodeScore> rankCandidates(List<NodeScore> candidates) {
        Map<String, NodeScore> bestByNode = new LinkedHashMap<>();
        for (NodeScore candidate : candidates) {
            IntentNode node = candidate.getNode();
            String key = StrUtil.blankToDefault(node.getId(), StrUtil.emptyIfNull(node.getName()));
            bestByNode.merge(key, candidate, (kept, current) -> kept.getScore() >= current.getScore() ? kept : current);
        }
        return bestByNode.values().stream()
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
    }

    /**
     * Take the highest-scoring candidate as primary and collect the others that share a path name with it.
     */
    private PathConflict collectPathConflicts(String question, List<NodeScore> ranked) {
        Map<String, IntentNode> nodeCache = new HashMap<>();
        NodeScore primary = ranked.get(0);
        List<IntentNode> primaryPath = buildNodePath(primary.getNode(), nodeCache);
        String normalizedQuestion = normalizeName(question);

        List<NodeScore> conflicts = new ArrayList<>();
        conflicts.add(primary);
        String topicName = null;
        for (NodeScore other : ranked.subList(1, ranked.size())) {
            List<IntentNode> otherPath = buildNodePath(other.getNode(), nodeCache);
            String hitName = detectConflictName(primaryPath, otherPath, normalizedQuestion);
            if (StrUtil.isBlank(hitName)) {
                continue;
            }
            conflicts.add(other);
            if (topicName == null) {
                topicName = hitName;
            }
        }

        if (conflicts.size() < 2) {
            return null;
        }
        log.info("候选意图路径重名[{}], 调 LLM 确认是否需要澄清, question={}", topicName, question);
        return new PathConflict(topicName, conflicts);
    }

    /**
     * Return the conflicting name of two paths, or null when there is no conflict.
     * A shared leaf name is a conflict by itself. A shared intermediate name after the fork also requires that the user question mentions that name; otherwise the user is not asking about that fork.
     */
    private String detectConflictName(List<IntentNode> primaryPath, List<IntentNode> otherPath, String normalizedQuestion) {
        if (CollUtil.isEmpty(primaryPath) || CollUtil.isEmpty(otherPath)) {
            return null;
        }

        IntentNode primaryLeaf = primaryPath.get(primaryPath.size() - 1);
        String leafName = normalizeName(primaryLeaf.getName());
        if (isComparableName(leafName) && leafName.equals(normalizeName(otherPath.get(otherPath.size() - 1).getName()))) {
            return primaryLeaf.getName();
        }

        // The common prefix is the same real nodes; sharing it is not ambiguity. Compare only the part after the fork.
        int common = commonPrefixLength(primaryPath, otherPath);
        Set<String> otherNames = otherPath.subList(common, otherPath.size()).stream()
                .map(node -> normalizeName(node.getName()))
                .filter(this::isComparableName)
                .collect(Collectors.toSet());
        for (IntentNode node : primaryPath.subList(common, primaryPath.size())) {
            String name = normalizeName(node.getName());
            if (isComparableName(name) && otherNames.contains(name) && normalizedQuestion.contains(name)) {
                return node.getName();
            }
        }
        return null;
    }

    /**
     * Walk parentId upward to build the full node path "root → ... → candidate".
     * visited guards against parent-child cycles from bad config. When a parent is missing, stop on the chain already collected.
     */
    private List<IntentNode> buildNodePath(IntentNode node, Map<String, IntentNode> nodeCache) {
        LinkedList<IntentNode> path = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        IntentNode current = node;
        while (current != null) {
            path.addFirst(current);
            visited.add(current.getId());
            String parentId = current.getParentId();
            if (StrUtil.isBlank(parentId) || visited.contains(parentId)) {
                break;
            }
            current = fetchNode(parentId, nodeCache);
        }
        return path;
    }

    /**
     * Longest common prefix length by node id.
     */
    private int commonPrefixLength(List<IntentNode> left, List<IntentNode> right) {
        int max = Math.min(left.size(), right.size());
        int index = 0;
        while (index < max && isSameNode(left.get(index), right.get(index))) {
            index++;
        }
        return index;
    }

    private boolean isSameNode(IntentNode left, IntentNode right) {
        return StrUtil.isNotBlank(left.getId()) && left.getId().equals(right.getId());
    }

    private boolean isComparableName(String normalizedName) {
        return StrUtil.isNotBlank(normalizedName) && normalizedName.length() >= MIN_COMPARABLE_NAME_LENGTH;
    }

    /**
     * Cache registry lookups for this decision. Missing parents are cached too, so the store is not hit again.
     */
    private IntentNode fetchNode(String nodeId, Map<String, IntentNode> nodeCache) {
        if (nodeCache.containsKey(nodeId)) {
            return nodeCache.get(nodeId);
        }
        IntentNode node = intentNodeRegistry.getNodeById(nodeId);
        nodeCache.put(nodeId, node);
        return node;
    }

    private List<NodeScore> trimRankedOptions(List<NodeScore> ranked) {
        int maxOptions = Optional.ofNullable(guidanceProperties.getMaxOptions()).orElse(ranked.size());
        if (ranked.size() <= maxOptions) {
            return ranked;
        }
        return ranked.subList(0, maxOptions);
    }

    private String buildPrompt(String topicName, List<NodeScore> ranked) {
        String options = renderOptions(ranked);
        return promptTemplateLoader.render(
                RAGConstant.GUIDANCE_PROMPT_PATH,
                Map.of(
                        "topic_name", StrUtil.blankToDefault(topicName, ""),
                        "options", options
                )
        );
    }

    private String renderOptions(List<NodeScore> ranked) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranked.size(); i++) {
            IntentNode node = ranked.get(i).getNode();
            String display = resolveOptionDisplay(node);
            sb.append(i + 1).append(") ").append(display).append("\n");
        }
        return sb.toString().trim();
    }

    private String resolveOptionDisplay(IntentNode node) {
        if (node == null) {
            return "";
        }
        if (StrUtil.isNotBlank(node.getFullPath())) {
            return node.getFullPath();
        }
        return StrUtil.blankToDefault(node.getName(), node.getId());
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        return cleaned.replaceAll("[\\p{Punct}\\s]+", "");
    }

    /**
     * Same-name path candidates waiting for LLM confirmation.
     */
    private record PathConflict(String topicName, List<NodeScore> ranked) {
    }

    private record AmbiguityGroup(String topicName, List<NodeScore> ranked) {
    }
}
