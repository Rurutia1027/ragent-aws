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

package com.nageoffer.ai.ragent.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chunk metadata resolver
 * <p>
 * Batch-loads rows for retrieved {@code chunkId}s (the vector-store primary key, also {@code t_knowledge_chunk.id})
 * and fills in the owning document (document id, index within the document, document title) so context assembly
 * can group by document and cite the source
 * <p>
 * Only the already-truncated final result set is loaded. The row count is small, so two batch queries are negligible
 */
@Service
@RequiredArgsConstructor
public class ChunkMetadataResolver {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;

    /**
     * Metadata of the document a chunk belongs to
     */
    public record ChunkMeta(String docId, Integer chunkIndex, String docName) {
    }

    /**
     * Resolves chunk metadata in batch
     *
     * @param chunkIds chunk ids that retrieval hit
     * @return chunkId to {@link ChunkMeta}. A chunk that missed is absent
     */
    public Map<String, ChunkMeta> resolve(Collection<String> chunkIds) {
        if (CollUtil.isEmpty(chunkIds)) {
            return Map.of();
        }
        Set<String> distinctIds = chunkIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        List<KnowledgeChunkDO> chunks = chunkMapper.selectByIds(distinctIds);
        if (CollUtil.isEmpty(chunks)) {
            return Map.of();
        }

        Set<String> docIds = chunks.stream()
                .map(KnowledgeChunkDO::getDocId)
                .filter(docId -> docId != null && !docId.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> docNameById = docIds.isEmpty()
                ? Map.of()
                : documentMapper.selectByIds(docIds).stream()
                .filter(doc -> doc.getId() != null && doc.getDocName() != null)
                .collect(Collectors.toMap(KnowledgeDocumentDO::getId, KnowledgeDocumentDO::getDocName, (a, b) -> a));

        Map<String, ChunkMeta> result = new HashMap<>(chunks.size());
        for (KnowledgeChunkDO chunk : chunks) {
            result.put(chunk.getId(), new ChunkMeta(
                    chunk.getDocId(),
                    chunk.getChunkIndex(),
                    docNameById.get(chunk.getDocId())));
        }
        return result;
    }

    /**
     * Resolves document titles by docId in batch
     * <p>
     * For evidence such as the graph that has no row in {@code t_knowledge_chunk} but already carries a docId, fill in
     * the real document title so it groups into the same document block as vector evidence from the same source
     *
     * @param docIds document ids
     * @return docId to document title. A miss is absent
     */
    public Map<String, String> resolveDocNames(Collection<String> docIds) {
        if (CollUtil.isEmpty(docIds)) {
            return Map.of();
        }
        Set<String> distinctIds = docIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return documentMapper.selectByIds(distinctIds).stream()
                .filter(doc -> doc.getId() != null && doc.getDocName() != null)
                .collect(Collectors.toMap(KnowledgeDocumentDO::getId, KnowledgeDocumentDO::getDocName, (a, b) -> a));
    }
}
