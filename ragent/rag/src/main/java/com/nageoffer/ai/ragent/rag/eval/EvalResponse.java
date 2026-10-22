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

package com.nageoffer.ai.ragent.rag.eval;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Eval retrieval response (retrieval evidence only, no LLM output)
 */
@Data
@Builder
public class EvalResponse {

    /**
     * Recalled business document IDs (deduplicated; file suffix stripped from t_knowledge_document.doc_name, aligned with the eval set's reference_doc_ids)
     */
    private List<String> retrievedDocIds;

    /**
     * Recalled chunk primary keys (RetrievedChunk.id, deduplicated; for debugging)
     */
    private List<String> retrievedChunkIds;

    /**
     * Recalled chunk texts (same order as retrievedChunkIds)
     */
    private List<String> retrievedContexts;

    /**
     * Business docId list aligned with retrievedContexts (chunk level, same length, nulls kept, not deduplicated)
     * The eval script reads by index when computing chunk-level metrics such as context_precision / context_recall
     */
    private List<String> retrievedContextDocIds;

    /**
     * Whether KB retrieval ran
     */
    private boolean hasKb;

    /**
     * Sub-questions (rewrite and split result)
     */
    private List<String> subIntents;

    /**
     * Top-1 intent leaf id for each sub-question, same order as subIntents (null when there is no candidate)
     * The eval script compares these with the eval set's intent_l2 to compute top-1 accuracy
     */
    private List<String> intentLeafIds;

    /**
     * Total elapsed time (milliseconds)
     */
    private long latencyMs;
}
