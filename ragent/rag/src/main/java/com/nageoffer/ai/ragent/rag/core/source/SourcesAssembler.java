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

package com.nageoffer.ai.ragent.rag.core.source;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.convention.SourceRef;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answer-source assembler
 * <p>
 * Dedup retrieved snippets (KB hits) by document, number them by relevance, fill source type and external link,
 * and produce a document-level source list. That list is both the SSE payload / panel display and the only numbering source for inline markers
 */
@Component
@RequiredArgsConstructor
public class SourcesAssembler {

    /**
     * Maximum excerpt length. Longer text is cut with an ellipsis
     */
    private static final int EXCERPT_MAX_LENGTH = 100;

    private static final String SOURCE_TYPE_URL = "url";
    private static final String SOURCE_TYPE_FEISHU = "feishu";

    private final KnowledgeDocumentMapper documentMapper;

    /**
     * Assemble the document-level source list from the retrieval context's intent slices
     *
     * @param intentChunks intent id to hit snippets (KB)
     * @return document-level sources; empty when there is no source
     */
    public List<SourceRef> assemble(Map<String, List<RetrievedChunk>> intentChunks) {
        if (CollUtil.isEmpty(intentChunks)) {
            return List.of();
        }

        // Group by docId and keep the highest-scoring snippet (excerpt and sort key)
        Map<String, RetrievedChunk> bestByDoc = new LinkedHashMap<>();
        intentChunks.values().stream()
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .filter(chunk -> chunk != null && StrUtil.isNotBlank(chunk.getDocId()))
                .forEach(chunk -> bestByDoc.merge(chunk.getDocId(), chunk,
                        (existing, candidate) -> score(candidate) > score(existing) ? candidate : existing));
        if (bestByDoc.isEmpty()) {
            return List.of();
        }

        // Sort by highest score descending; every document that entered the context must be able to receive a citation number
        List<RetrievedChunk> ordered = bestByDoc.values().stream()
                .sorted(Comparator.comparingDouble(SourcesAssembler::score).reversed()
                        .thenComparing(RetrievedChunk::getDocId))
                .toList();

        // Fill source type and external link in batch
        List<String> docIds = ordered.stream().map(RetrievedChunk::getDocId).toList();
        Map<String, KnowledgeDocumentDO> docs = loadDocs(docIds);

        List<SourceRef> sources = new ArrayList<>(ordered.size());
        int index = 1;
        for (RetrievedChunk chunk : ordered) {
            KnowledgeDocumentDO doc = docs.get(chunk.getDocId());
            String sourceType = doc != null ? doc.getSourceType() : null;
            sources.add(SourceRef.builder()
                    .index(index++)
                    .docId(chunk.getDocId())
                    .docName(resolveDocName(chunk, doc))
                    .sourceType(sourceType)
                    .fileType(doc != null ? doc.getFileType() : null)
                    .url(resolveUrl(sourceType, doc))
                    .excerpt(StrUtil.maxLength(StrUtil.trim(chunk.getText()), EXCERPT_MAX_LENGTH))
                    .build());
        }
        return sources;
    }

    private Map<String, KnowledgeDocumentDO> loadDocs(List<String> docIds) {
        List<KnowledgeDocumentDO> docs = documentMapper.selectBatchIds(docIds);
        Map<String, KnowledgeDocumentDO> map = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(docs)) {
            docs.forEach(doc -> map.put(doc.getId(), doc));
        }
        return map;
    }

    /**
     * External original link: only url/feishu sources carry file and use docId preview to extract body text
     */
    private String resolveUrl(String sourceType, KnowledgeDocumentDO doc) {
        if (doc == null || sourceType == null) {
            return null;
        }
        if (SOURCE_TYPE_URL.equalsIgnoreCase(sourceType) || SOURCE_TYPE_FEISHU.equalsIgnoreCase(sourceType)) {
            return StrUtil.blankToDefault(doc.getSourceLocation(), null);
        }
        return null;
    }

    private String resolveDocName(RetrievedChunk chunk, KnowledgeDocumentDO doc) {
        if (StrUtil.isNotBlank(chunk.getDocName())) {
            return chunk.getDocName();
        }
        return doc != null ? doc.getDocName() : null;
    }

    private static double score(RetrievedChunk chunk) {
        return chunk.getScore() != null ? chunk.getScore() : 0D;
    }
}
