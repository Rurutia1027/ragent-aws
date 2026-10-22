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

package com.nageoffer.ai.ragent.rag.core.graph;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * Split of graph evidence by knowledge-base ownership.
 * <p>
 * A single LightRAG instance is a single graph, and one query sees the whole graph, so ownership
 * can only be decided on the result side by file_path. The client assigns ownership and the channel
 * splits quotas: when the filter is empty, everything lands in {@code matched}.
 *
 * @param matched   evidence that hits the target knowledge base, ordered by graph rank
 * @param unmatched evidence outside the target knowledge base, ordered by graph rank
 */
public record GraphEvidence(List<RetrievedChunk> matched, List<RetrievedChunk> unmatched) {

    public static GraphEvidence empty() {
        return new GraphEvidence(List.of(), List.of());
    }
}
