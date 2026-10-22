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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapBuilder;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.infra.util.LLMResponseCleaner;
import com.nageoffer.ai.ragent.infra.util.LogSafe;
import com.nageoffer.ai.ragent.rag.config.OrchestrationMode;
import com.nageoffer.ai.ragent.rag.config.OrchestrationProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.IntentNodeDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.IntentNodeMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.INTENT_CLASSIFIER_PROMPT_PATH;

/**
 * LLM tree intent classifier (serial implementation).
 * <p>
 * Sends every intent node available in the current orchestration mode to the LLM in one call for scoring. Suited to a small intent set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultIntentClassifier implements IntentClassifier, IntentNodeRegistry {

    private final LLMService llmService;
    private final IntentNodeMapper intentNodeMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final IntentTreeCacheManager intentTreeCacheManager;
    private final OrchestrationProperties orchestrationProperties;

    /**
     * Load the intent tree from Redis and build the in-memory structure.
     * Every call re-reads Redis so the data is current.
     */
    private IntentTreeData loadIntentTreeData() {
        // 1. Read from Redis (a miss loads from the database automatically).
        List<IntentNode> roots = intentTreeCacheManager.getIntentTreeFromCache();

        // 2. If Redis is also empty, load from the database and cache.
        if (CollUtil.isEmpty(roots)) {
            roots = loadIntentTreeFromDB();
            if (!roots.isEmpty()) {
                intentTreeCacheManager.saveIntentTreeToCache(roots);
            }
        }

        // 3. Build the in-memory structure (temporary).
        if (CollUtil.isEmpty(roots)) {
            return new IntentTreeData(List.of(), List.of(), Map.of());
        }

        List<IntentNode> allNodes = flatten(roots);
        List<IntentNode> leafNodes = allNodes.stream()
                .filter(IntentNode::isLeaf)
                .collect(Collectors.toList());
        Map<String, IntentNode> id2Node = allNodes.stream()
                .collect(Collectors.toMap(IntentNode::getId, n -> n));

        log.debug("意图树数据加载完成, 总节点数: {}, 叶子节点数: {}", allNodes.size(), leafNodes.size());

        return new IntentTreeData(allNodes, leafNodes, id2Node);
    }

    @Override
    public IntentNode getNodeById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        IntentTreeData data = loadIntentTreeData();
        return data.id2Node.get(id);
    }

    @Override
    public List<IntentNode> listMcpToolNodes() {
        return loadIntentTreeData().leafNodes.stream()
                .filter(IntentNode::isMCP)
                .filter(node -> node.getMcpToolId() != null && !node.getMcpToolId().isBlank())
                .sorted(Comparator.comparing(IntentNode::getId))
                .toList();
    }

    /**
     * Intent-tree data structure (temporary, not persisted).
     */
    private record IntentTreeData(
            List<IntentNode> allNodes,
            List<IntentNode> leafNodes,
            Map<String, IntentNode> id2Node
    ) {
    }

    private List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> result = new ArrayList<>();
        Deque<IntentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            IntentNode n = stack.pop();
            result.add(n);
            if (n.getChildren() != null) {
                for (IntentNode child : n.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return result;
    }

    /**
     * Classify every "leaf category node". The LLM outputs a score for each category.
     * - The result is already sorted by score descending.
     */
    @Override
    public List<NodeScore> classifyTargets(String question) {
        // Always read the latest data from Redis.
        IntentTreeData data = loadIntentTreeData();
        if (data.leafNodes.isEmpty()) {
            log.debug("意图树没有可用叶子节点，跳过 LLM 意图识别");
            return List.of();
        }

        // The Agent knowledge-base tool classifies KB only; Workflow also keeps SYSTEM replies.
        List<IntentNode> candidates = data.leafNodes.stream()
                .filter(node -> node.isKB()
                        || (orchestrationProperties.getMode() == OrchestrationMode.WORKFLOW && node.isSystem()))
                .toList();
        if (candidates.isEmpty()) {
            log.debug("当前编排模式没有可用意图叶子节点，跳过 LLM 意图识别, mode={}",
                    orchestrationProperties.getMode());
            return List.of();
        }
        Map<String, IntentNode> candidateById = candidates.stream()
                .collect(Collectors.toMap(IntentNode::getId, node -> node));

        String systemPrompt = buildPrompt(candidates);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(question)
                ))
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();

        // Standard-tier call. A failed call or invalid JSON returns no intents (downstream treats an empty intent list as "no intent").
        String raw;
        try {
            raw = llmService.chat(request);
        } catch (Exception e) {
            log.warn("意图识别 LLM 调用失败，返回空意图", e);
            return List.of();
        }
        return parseScores(raw, candidateById, question);
    }

    /**
     * Parse intent scores and return them by score descending. Any parse failure or malformed payload returns an empty list.
     */
    private List<NodeScore> parseScores(String raw, Map<String, IntentNode> candidateById, String question) {
        try {
            // Strip a possible markdown code fence.
            String cleanedRaw = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonElement root = JsonParser.parseString(cleanedRaw);
            JsonArray arr;
            if (root.isJsonArray()) {
                arr = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("results")) {
                // Tolerate an extra wrapper { "results": [...] } around the model output.
                arr = root.getAsJsonObject().getAsJsonArray("results");
            } else {
                log.warn("LLM 返回了非预期的 JSON 格式, 原始响应: {}", LogSafe.preview(raw));
                return List.of();
            }

            List<NodeScore> scores = new ArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                if (!obj.has("id") || !obj.has("score")) continue;

                String id = obj.get("id").getAsString();
                IntentNode node = candidateById.get(id);
                if (node == null) {
                    log.warn("LLM 返回了未知或当前模式不可用的意图节点 ID: {}, 已跳过", id);
                    continue;
                }

                scores.add(new NodeScore(node, obj.get("score").getAsDouble()));
            }

            // Sort descending.
            scores.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());

            log.info("当前问题：{}\n意图识别树如下所示：{}\n",
                    question,
                    JSONUtil.toJsonPrettyStr(
                            scores.stream().map(ns -> MapBuilder.<String, Object>create(true)
                                    .put("id", ns.getNode().getId())
                                    .put("path", ns.getNode().getFullPath())
                                    .put("score", ns.getScore())
                                    .build()).toList()
                    )
            );
            return scores;
        } catch (Exception e) {
            log.warn("意图打分解析失败, 原始响应: {}", LogSafe.preview(raw), e);
            return List.of();
        }
    }

    /**
     * Build the prompt for the LLM:
     * - List id / path / description / example questions of the candidate leaf nodes available in the current orchestration mode.
     * - The LLM may choose only among those ids and must output a JSON array: [{"id": "...", "score": 0.9, "reason": "..."}].
     * - Stress that if the question only mentions "OA system", do not pick an "insurance system" category.
     * - Every node carries a type marker so the template can tell knowledge-base retrieval from system interaction.
     */
    private String buildPrompt(List<IntentNode> leafNodes) {
        StringBuilder sb = new StringBuilder();

        for (IntentNode node : leafNodes) {
            sb.append("- id=").append(node.getId()).append("\n");
            sb.append("  path=").append(node.getFullPath()).append("\n");
            sb.append("  description=").append(node.getDescription()).append("\n");

            sb.append(node.isSystem() ? "  type=SYSTEM\n" : "  type=KB\n");

            if (node.getExamples() != null && !node.getExamples().isEmpty()) {
                sb.append("  examples=");
                sb.append(String.join(" / ", node.getExamples()));
                sb.append("\n");
            }
            sb.append("\n");
        }

        return promptTemplateLoader.render(
                INTENT_CLASSIFIER_PROMPT_PATH,
                Map.of("intent_list", sb.toString())
        );
    }

    private List<IntentNode> loadIntentTreeFromDB() {
        // 1. Load every node that is not deleted and is enabled (flat list).
        List<IntentNodeDO> intentNodeDOList = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
                        .eq(IntentNodeDO::getEnabled, 1)
        );

        if (intentNodeDOList.isEmpty()) {
            return List.of();
        }

        // 2. DO -> IntentNode (first pass: build every node and put it in the map).
        Map<String, IntentNode> id2Node = new HashMap<>();
        for (IntentNodeDO each : intentNodeDOList) {
            IntentNode node = BeanUtil.toBean(each, IntentNode.class);
            // Map the database code onto IntentNode id/parentId.
            node.setId(each.getIntentCode());
            node.setParentId(each.getParentCode());
            node.setMcpToolId(each.getMcpToolId());
            node.setRequireConfirm(Objects.equals(each.getRequireConfirm(), 1));
            node.setExamples(parseExamples(each.getExamples()));
            if (CollUtil.isEmpty(each.getCollectionNames())) {
                node.setCollectionNames(
                        each.getCollectionName() == null || each.getCollectionName().isBlank()
                                ? List.of()
                                : List.of(each.getCollectionName())
                );
            }
            // Keep children non-null (later add must not NPE).
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            id2Node.put(node.getId(), node);
        }

        // 3. Second pass: wire parent -> children from parentId.
        List<IntentNode> roots = new ArrayList<>();
        for (IntentNode node : id2Node.values()) {
            String parentId = node.getParentId();
            if (parentId == null || parentId.isBlank()) {
                // No parentId: treat as a root.
                roots.add(node);
                continue;
            }

            IntentNode parent = id2Node.get(parentId);
            if (parent == null) {
                // Parent not found: fall back to a root so the node is not dropped.
                roots.add(node);
                continue;
            }

            // Append to the parent's children.
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(node);
        }

        // 4. Fill fullPath (same logic as the original fillFullPath).
        fillFullPath(roots, null);

        return roots;
    }

    /**
     * examples is stored as a GSON-serialized JSON array string.
     * BeanUtil only strips the brackets and splits on English commas, so the quotes around each element would land in the intent prompt as-is.
     */
    private List<String> parseExamples(String examples) {
        if (examples == null || examples.isBlank()) {
            return List.of();
        }
        try {
            JsonElement root = JsonParser.parseString(examples);
            if (!root.isJsonArray()) {
                log.warn("意图节点 examples 不是 JSON 数组, 原始值: {}", LogSafe.preview(examples));
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray()) {
                if (el.isJsonPrimitive()) {
                    result.add(el.getAsString());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("意图节点 examples 解析失败, 原始值: {}", LogSafe.preview(examples), e);
            return List.of();
        }
    }

    /**
     * Fill fullPath. The result looks like:
     * - Group IT
     * - Group IT > HR
     * - Business systems > OA > system overview
     */
    private void fillFullPath(List<IntentNode> nodes, IntentNode parent) {
        if (nodes == null) return;

        for (IntentNode node : nodes) {
            if (parent == null) {
                node.setFullPath(node.getName());
            } else {
                node.setFullPath(parent.getFullPath() + " > " + node.getName());
            }

            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                fillFullPath(node.getChildren(), node);
            }
        }
    }
}
