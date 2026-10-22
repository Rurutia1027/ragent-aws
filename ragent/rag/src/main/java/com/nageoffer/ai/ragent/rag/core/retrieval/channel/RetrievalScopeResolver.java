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

package com.nageoffer.ai.ragent.rag.core.retrieval.channel;

import cn.hutool.core.collection.CollUtil;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScoreFilters;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retrieval-scope resolver
 * <p>
 * The engine computes it once per sub-question and stores it on {@link SearchContext}. Channels only read it,
 * so one sub-question cannot have the vector channel go global while the keyword channel goes directed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalScopeResolver {

    private final SearchChannelProperties properties;
    private final KbCollectionProvider kbCollectionProvider;

    /**
     * Resolve the retrieval scope for this request
     * <p>
     * Look only at the highest KB-intent score: narrow only when it reaches the confidence threshold. Intent count does not participate — one more low-score intent should not make the system more accurate
     */
    public RetrievalScope resolve(List<SubQuestionIntent> subIntents) {
        List<String> activeCollections = kbCollectionProvider.listActiveCollections();
        List<NodeScore> kbIntents = extractKbIntents(subIntents);
        double topScore = kbIntents.stream().mapToDouble(NodeScore::getScore).max().orElse(0.0);

        if (kbIntents.isEmpty()) {
            log.info("未识别出有效 KB 意图，检索走全局作用域");
            return RetrievalScope.global(topScore, activeCollections);
        }
        double threshold = properties.getScope().getConfidenceThreshold();
        if (topScore < threshold) {
            log.info("KB 意图置信度过低（{} < {}），检索走全局作用域", topScore, threshold);
            return RetrievalScope.global(topScore, activeCollections);
        }

        Set<String> bound = new LinkedHashSet<>(NodeScoreFilters.kbCollections(kbIntents));
        // Intent bindings and the knowledge-base table are two data sets; deleting a base does not rewrite intent nodes. A dangling name is just an empty search for vector / ES,
        // but the graph matches that name to leftovers of the deleted base, so the bound set is intersected with valid collections first
        Set<String> targets = new LinkedHashSet<>(bound);
        targets.retainAll(activeCollections);
        if (targets.isEmpty()) {
            log.warn("KB 意图绑定的知识库均已失效（{}），检索退化为全局作用域", bound);
            return RetrievalScope.global(topScore, activeCollections);
        }
        if (targets.size() < bound.size()) {
            bound.removeAll(targets);
            log.warn("KB 意图绑定中已失效的知识库（{}）不参与检索", bound);
        }
        List<String> supplement = activeCollections.stream()
                .filter(collection -> !targets.contains(collection))
                .toList();
        log.info("KB 意图置信度充足（{}），检索收窄到 {} 个命中库，补充范围 {} 个库", topScore, targets.size(), supplement.size());
        return new RetrievalScope(true, topScore, kbIntents, List.copyOf(targets), supplement);
    }

    /**
     * KB intents that meet the minimum score, actually bind a collection, and are deduplicated by intent node
     * <p>
     * Dedup is required: intents are recognized per sub-question, so several sub-questions hitting the same node produce several NodeScores.
     * The directed path fans out once per NodeScore — without dedup the same collection is queried more than once, the same chunk occupies several ranks in the channel's raw list,
     * and downstream RRF sums by rank and doubles its score. It also inflates "channel capacity" and, with it, the supplement-path quota
     * <p>
     * An intent with an empty binding retrieves nothing and must not raise the confidence decision
     */
    private List<NodeScore> extractKbIntents(List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents)) {
            return List.of();
        }
        double minScore = properties.getScope().getMinIntentScore();
        List<NodeScore> allScores = subIntents.stream()
                .flatMap(si -> si.nodeScores().stream())
                .toList();
        return NodeScoreFilters.kb(allScores, minScore).stream()
                .filter(nodeScore -> !nodeScore.getNode().getEffectiveCollectionNames().isEmpty())
                .collect(Collectors.toMap(
                        nodeScore -> nodeScore.getNode().getId(),
                        nodeScore -> nodeScore,
                        // Keep the highest score for the same node: the score is only for the confidence decision, and the highest score is that node's real hit strength
                        (left, right) -> left.getScore() >= right.getScore() ? left : right,
                        LinkedHashMap::new))
                .values().stream()
                .toList();
    }
}
