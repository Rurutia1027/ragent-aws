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

package com.nageoffer.ai.ragent.rag.core.retrieval.postprocessor;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.knowledge.service.impl.ChunkMetadataResolver;
import com.nageoffer.ai.ragent.knowledge.service.impl.ChunkMetadataResolver.ChunkMeta;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Metadata enrichment post-processor
 * <p>
 * At the end of the chain (after rerank), looks up document ownership for the final top-K by chunkId
 * (document id, index within the document, document title) so context assembly can group by document and cite the source
 * <p>
 * Enrich only; do not re-rank. The incoming relevance order stays
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataEnrichmentPostProcessor implements SearchResultPostProcessor {

    private final ChunkMetadataResolver chunkMetadataResolver;
    private final RAGConfigProperties ragConfigProperties;

    @Override
    public String getName() {
        return "MetadataEnrichment";
    }

    @Override
    public int getOrder() {
        return 20;  // After Rerank(10), runs at the end of the chain
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return ragConfigProperties.getContextEnrichEnabled();
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        List<String> chunkIds = chunks.stream().map(RetrievedChunk::getId).toList();
        Map<String, ChunkMeta> metaById = chunkMetadataResolver.resolve(chunkIds);

        // 1) Enrich by chunkId: for vector / keyword evidence, chunk.id is the vector-store primary key. Look up docId / index / title
        // Enrich in place and keep the relevance order
        for (RetrievedChunk chunk : chunks) {
            ChunkMeta meta = metaById.get(chunk.getId());
            if (meta == null) {
                continue;
            }
            chunk.setDocId(meta.docId());
            chunk.setChunkIndex(meta.chunkIndex());
            chunk.setDocName(meta.docName());
        }

        // 2) Fill titles by docId: graph evidence has a chunk.id that is not a vector-store primary key, so the previous step missed it, but it already carries an owning docId.
        // Use that to fill the real document title so it groups into the same document block as vector evidence from the same source
        fillDocNamesByDocId(chunks);
        return chunks;
    }

    /**
     * For evidence that the chunkId step did not title, but that already carries a docId (typically graph evidence), look up the real document title by docId
     */
    private void fillDocNamesByDocId(List<RetrievedChunk> chunks) {
        List<String> pendingDocIds = chunks.stream()
                .filter(c -> StrUtil.isBlank(c.getDocName()) && StrUtil.isNotBlank(c.getDocId()))
                .map(RetrievedChunk::getDocId)
                .toList();
        if (pendingDocIds.isEmpty()) {
            return;
        }
        Map<String, String> docNameById = chunkMetadataResolver.resolveDocNames(pendingDocIds);
        if (docNameById.isEmpty()) {
            return;
        }
        for (RetrievedChunk chunk : chunks) {
            if (StrUtil.isBlank(chunk.getDocName()) && StrUtil.isNotBlank(chunk.getDocId())) {
                String docName = docNameById.get(chunk.getDocId());
                if (StrUtil.isNotBlank(docName)) {
                    chunk.setDocName(docName);
                }
            }
        }
    }
}
