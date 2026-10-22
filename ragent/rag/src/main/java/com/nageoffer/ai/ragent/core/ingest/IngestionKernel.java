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

package com.nageoffer.ai.ragent.core.ingest;

/**
 * Ingestion kernel: a fixed five-step skeleton. Callers cannot skip, reorder, or replace a step
 * <pre>
 *   (1) identity   bytes + file name --> MIME          the only detection on the path; no argument to pass wrong
 *   (2) parse      (MIME x profile) --> List&lt;Block&gt;
 *   (3) chunk      Block type -> chunker + budget --> List&lt;Chunk&gt;
 *   (4) embed      embed, and check dimension here
 *   (5) index      ChunkSink fan-out; the transaction boundary is here
 * </pre>
 * Fetching bytes happens before the kernel. Task-status transitions and ingestion logs belong to the outer layer
 */
public interface IngestionKernel {

    /**
     * Runs one complete ingestion: parse -> chunk -> embed -> store
     *
     * @param doc    document identity; decides asset ownership and where rows are stored
     * @param bytes  file bytes
     * @param spec   document-level config: parse profile + chunk budget
     * @param target vector target: logical partition + embedding model + dimension
     * @return ingestion outcome
     */
    IngestionOutcome run(DocumentRef doc,
                         byte[] bytes,
                         IngestionSpec spec,
                         VectorTarget target);
}
