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

package com.nageoffer.ai.ragent.core.ingest.sink;

import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.DocumentRef;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;

import java.util.List;

/**
 * Index-target port. The kernel knows only this interface; implementations live in their own modules
 * <p>
 * The kernel injects {@code List<ChunkSink>} and fans out. Order among implementations is Spring {@code @Order}.
 * All of them run in the same transaction as the kernel's store step. Only "replace as a whole" is exposed, not
 * separate delete and write methods. The implementation guarantees delete-then-insert order (the vector decorator chain relies on that for upsert)
 */
public interface ChunkSink {

    /**
     * Replaces this document's existing chunks with the given chunks. An empty list means the document produces no chunks
     */
    void replaceDocument(VectorTarget target, DocumentRef doc, List<EmbeddedChunk> chunks);

    /**
     * Removes every chunk of this document
     */
    void deleteDocument(VectorTarget target, DocumentRef doc);
}
