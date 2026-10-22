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

package com.nageoffer.ai.ragent.ingestion.domain.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chunker settings.
 * Config of the document chunk node: strategy, chunk size, overlap, and so on
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkerSettings {

    /**
     * Chunk strategy (deprecated: the field stays so the pipeline editor UI does not have to change; the backend no longer reads it)
     * <p>
     * How to cut is decided solely by document structure; the user only controls the budget. This enum never made a
     * difference on the real path: every parser emits structured Blocks, chunking always takes the block-aware branch, and the strategy parameter was discarded
     */
    private String strategy;

    /**
     * Target chunk size (characters or tokens)
     */
    private Integer chunkSize;

    /**
     * Overlap between adjacent chunks.
     * Keeps context continuous
     */
    private Integer overlapSize;

    /**
     * Custom separator.
     * The boundary characters used to split text
     */
    private String separator;

    /**
     * Max data rows per table chunk (hard cap of TableChunker on the block-aware path).
     * Actual chunk size is driven mainly by the chunkSize budget; this only stops a very narrow table from packing too many rows into one chunk.
     * Nullable. When empty, ChunkerNode uses the default
     */
    private Integer rowsPerChunk;
}
