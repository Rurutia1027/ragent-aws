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

package com.nageoffer.ai.ragent.rag.core.vector.sink;

import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.DocumentRef;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.core.ingest.sink.ChunkSink;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Vector sink: delegates to the existing vector write service
 * <p>
 * The injected {@link VectorStoreService} is a decorator chain (graph sync, then keyword sync, then PG / Milvus). A backend that is off
 * does not register its decorator. To promote keyword or graph from a side effect of vector writes to a first-class sink, add a {@link ChunkSink}
 * bean and remove the matching decorator. The kernel and the writer do not change
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class VectorChunkSink implements ChunkSink {

    private final VectorStoreService vectorStoreService;

    @Override
    public void replaceDocument(VectorTarget target, DocumentRef doc, List<EmbeddedChunk> chunks) {
        // Delete then create: graph sync on the decorator chain depends on this order for upsert semantics.
        // The order stays inside the implementation and is not exposed to callers
        vectorStoreService.deleteDocumentVectors(target.partition(), doc.docId());
        if (!chunks.isEmpty()) {
            vectorStoreService.indexDocumentChunks(target.partition(), doc.docId(), chunks);
        }
    }

    @Override
    public void deleteDocument(VectorTarget target, DocumentRef doc) {
        vectorStoreService.deleteDocumentVectors(target.partition(), doc.docId());
    }
}
