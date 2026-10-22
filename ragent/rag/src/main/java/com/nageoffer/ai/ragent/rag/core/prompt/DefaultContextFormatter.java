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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunkKey;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CONTEXT_FORMAT_PATH;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultContextFormatter implements ContextFormatter {

    private final PromptTemplateLoader templateLoader;

    @Override
    public String formatKbContext(List<NodeScore> kbIntents,
                                  Set<String> eligibleIntentIds,
                                  List<RetrievedChunk> rerankedChunks,
                                  int contextTopK) {
        if (CollUtil.isEmpty(rerankedChunks)) {
            return "";
        }

        List<NodeScore> eligibleIntents = eligibleIntents(kbIntents, eligibleIntentIds);
        String branch = switch (eligibleIntents.size()) {
            case 0 -> "无可用意图";
            case 1 -> "单意图";
            default -> "多意图";
        };
        log.info("检索归因 - 提示词分支: {}, 可用意图: {}",
                branch,
                eligibleIntents.stream().map(ns -> ns.getNode().getName()).toList());
        if (eligibleIntents.isEmpty()) {
            return formatChunksWithoutIntent(rerankedChunks, contextTopK);
        }
        if (eligibleIntents.size() > 1) {
            return formatMultiIntentContext(eligibleIntents, rerankedChunks, contextTopK);
        }
        return formatSingleIntentContext(eligibleIntents.get(0), rerankedChunks, contextTopK);
    }

    private List<NodeScore> eligibleIntents(List<NodeScore> kbIntents,
                                            Set<String> eligibleIntentIds) {
        if (CollUtil.isEmpty(kbIntents) || CollUtil.isEmpty(eligibleIntentIds)) {
            return List.of();
        }
        return kbIntents.stream()
                .filter(nodeScore -> nodeScore != null && nodeScore.getNode() != null)
                .filter(nodeScore -> eligibleIntentIds.contains(nodeScore.getNode().getId()))
                .toList();
    }

    /**
     * Format single-intent context.
     */
    private String formatSingleIntentContext(NodeScore nodeScore, List<RetrievedChunk> rerankedChunks, int topK) {
        String snippet = StrUtil.emptyIfNull(nodeScore.getNode().getPromptSnippet()).trim();
        String docBlocks = renderChunksGroupedByDoc(distinctChunks(rerankedChunks), topK);
        return renderKbSection(renderSnippetRules(snippet), docBlocks);
    }

    /**
     * Format multi-intent context.
     */
    private String formatMultiIntentContext(List<NodeScore> kbIntents,
                                            List<RetrievedChunk> rerankedChunks,
                                            int topK) {
        // 1. Merge the answer rules of every intent.
        List<String> snippets = kbIntents.stream()
                .map(ns -> ns.getNode().getPromptSnippet())
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();

        String snippetSection = "";
        if (!snippets.isEmpty()) {
            String numberedRules = IntStream.range(0, snippets.size())
                    .mapToObj(i -> (i + 1) + ". " + snippets.get(i))
                    .collect(Collectors.joining("\n"));
            snippetSection = renderSnippetRules(numberedRules);
        }

        // 2. Merge document chunks from every intent (dedupe by chunk id, keep relevance order).
        List<RetrievedChunk> allChunks = distinctChunks(rerankedChunks);

        if (allChunks.isEmpty()) {
            return snippetSection;
        }

        String docBlocks = renderChunksGroupedByDoc(allChunks, topK);
        return renderKbSection(snippetSection, docBlocks);
    }

    private String formatChunksWithoutIntent(List<RetrievedChunk> rerankedChunks, int topK) {
        List<RetrievedChunk> chunks = distinctChunks(rerankedChunks);
        if (chunks.isEmpty()) {
            return "";
        }

        String docBlocks = renderChunksGroupedByDoc(chunks, topK);
        return renderKbSection("", docBlocks);
    }

    private List<RetrievedChunk> distinctChunks(List<RetrievedChunk> chunks) {
        Map<String, RetrievedChunk> distinct = new LinkedHashMap<>();
        chunks.forEach(chunk -> distinct.putIfAbsent(RetrievedChunkKey.of(chunk), chunk));
        return new ArrayList<>(distinct.values());
    }

    private String renderKbSection(String snippetSection, String docBlocks) {
        return templateLoader.renderSection(CONTEXT_FORMAT_PATH, "kb-section", Map.of(
                "snippet_section", snippetSection,
                "doc_blocks", docBlocks
        ));
    }

    private String renderSnippetRules(String snippet) {
        if (StrUtil.isBlank(snippet)) {
            return "";
        }
        return templateLoader.renderSection(CONTEXT_FORMAT_PATH, "snippet-rules", Map.of("rules", snippet));
    }

    /**
     * Render the chunk list grouped by document.
     * <p>
     * Documents are ordered by relevance (the position of each document's first hit in the original list, which is that document's best chunk rank).
     * Inside a document, restore source order by {@code chunkIndex} ascending. Chunks missing docId each form their own group and stay in place.
     */
    private String renderChunksGroupedByDoc(List<RetrievedChunk> chunks, int topK) {
        long limit = topK > 0 ? topK : Long.MAX_VALUE;
        List<RetrievedChunk> limited = chunks.stream().limit(limit).toList();
        if (limited.isEmpty()) {
            return "";
        }

        // Group by docId: LinkedHashMap keeps first-seen order, which is the relevance order across documents. Chunks with a blank docId each form their own group.
        LinkedHashMap<String, List<RetrievedChunk>> groups = new LinkedHashMap<>();
        int anonymousSeq = 0;
        for (RetrievedChunk chunk : limited) {
            String key = StrUtil.isNotBlank(chunk.getDocId()) ? chunk.getDocId() : "__nodoc__" + (anonymousSeq++);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(chunk);
        }

        return groups.values().stream()
                .map(this::renderDocBlock)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Render one document block: sort the group by index, then concatenate, carrying only the internal docId as an anchor.
     * <p>
     * Document titles are deliberately not injected. Once a title enters the context, the model writes attributions such as "from XX",
     * and a prompt-level ban does not hold. Documents are distinguished by the {@code ref} number; the document name is shown only in the frontend source list.
     */
    private String renderDocBlock(List<RetrievedChunk> group) {
        List<RetrievedChunk> ordered = group.stream()
                .sorted(Comparator.comparing(RetrievedChunk::getChunkIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        String chunks = joinDocBody(ordered);
        String docId = sanitizeAttribute(resolveDocId(group));
        if (StrUtil.isNotBlank(docId)) {
            return templateLoader.renderSection(CONTEXT_FORMAT_PATH, "kb-doc-block", Map.of(
                    "doc_id", docId,
                    "chunks", chunks
            ));
        }
        return templateLoader.renderSection(CONTEXT_FORMAT_PATH, "kb-doc-block-anonymous", Map.of(
                "chunks", chunks
        ));
    }

    /**
     * Concatenate text inside a group: chunks of the same document, sorted by index, joined with newlines.
     */
    private String joinDocBody(List<RetrievedChunk> ordered) {
        return ordered.stream()
                .map(RetrievedChunk::getText)
                .map(StrUtil::emptyIfNull)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Strip characters in attribute values that would break the pseudo-tag structure (quotes, angle brackets).
     */
    private String sanitizeAttribute(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        return value.replaceAll("[\"<>]", "").trim();
    }

    private String resolveDocId(List<RetrievedChunk> group) {
        return group.stream()
                .map(RetrievedChunk::getDocId)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse("");
    }

}
