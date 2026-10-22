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

package com.nageoffer.ai.ragent.rag.core.vector.decorator;

import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.rag.core.graph.GraphFileSource;
import com.nageoffer.ai.ragent.rag.core.graph.LightRagClient;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Graph-sync decorator around vector writes
 * <p>
 * Same shape as {@link KeywordSyncingVectorStoreService}. Wraps the real {@link VectorStoreService} and, after a successful write or delete,
 * keeps the LightRAG knowledge graph in sync. Graph extraction is document-level (entities merged across chunks), so sync is per document. A write concatenates the chunks on hand into full text, a delete clears by document, and the existing "delete then create" rebuild order is naturally an upsert. Chunk-level
 * updateChunk / deleteChunkById / deleteChunksByIds do not sync the graph; re-ingesting the whole document refreshes it, and this does not add a
 * chunkId-to-docId lookup for them. Graph writes are best-effort: failure is logged only, does not roll back vectors, and does not stop the main path
 */
@Slf4j
public class GraphSyncingVectorStoreService implements VectorStoreService {

    private final VectorStoreService delegate;
    private final LightRagClient lightRagClient;

    public GraphSyncingVectorStoreService(VectorStoreService delegate, LightRagClient lightRagClient) {
        this.delegate = delegate;
        this.lightRagClient = lightRagClient;
    }

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<EmbeddedChunk> chunks) {
        delegate.indexDocumentChunks(collectionName, docId, chunks);
        syncGraph(docId, () -> lightRagClient.insertText(concatContent(chunks), fileSource(collectionName, docId)));
    }

    @Override
    public void updateChunk(String collectionName, String docId, EmbeddedChunk chunk) {
        delegate.updateChunk(collectionName, docId, chunk);
        // Sub-document granularity. Phase 1 does not sync the graph on its own (see the class comment)
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        delegate.deleteDocumentVectors(collectionName, docId);
        syncGraph(docId, () -> lightRagClient.deleteByDoc(docId));
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        delegate.deleteChunkById(collectionName, chunkId);
        // Sub-document granularity. Phase 1 does not sync the graph on its own (see the class comment)
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        delegate.deleteChunksByIds(collectionName, chunkIds);
        // Sub-document granularity. Phase 1 does not sync the graph on its own (see the class comment)
    }

    /**
     * Graph file_source encoding. Ownership checks on the read side and delete matching depend on the same format; see {@link GraphFileSource}
     */
    private String fileSource(String collectionName, String docId) {
        return GraphFileSource.encode(collectionName, docId);
    }

    /**
     * Join chunk bodies into the document text. Blank chunks are skipped
     */
    private String concatContent(List<EmbeddedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        return chunks.stream()
                .map(EmbeddedChunk::content)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Run graph sync best-effort. Failure only warns and does not affect the vector path
     */
    private void syncGraph(String docId, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("图谱同步失败，已跳过 docId={}", docId, e);
        }
    }
}
