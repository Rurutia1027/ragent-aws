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

import com.nageoffer.ai.ragent.core.chunk.model.Chunk;

import java.util.List;

/**
 * Ingestion outcome: chunk count, timings, and the parser that was hit, enough for the outer layer to write an ingestion log and update stats
 * <p>
 * Stops at {@link Chunk}. Vectors are already written to each index backend by the kernel and are not returned again with the result
 *
 * @param mimeType   detected real MIME type
 * @param parserType parser type that actually matched
 * @param blockCount number of Blocks produced by parsing
 * @param chunks     chunks actually stored
 * @param timings    duration of each stage
 */
public record IngestionOutcome(
        String mimeType,
        String parserType,
        int blockCount,
        List<Chunk> chunks,
        IngestionTimings timings
) {

    public IngestionOutcome {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        timings = timings == null ? IngestionTimings.zero() : timings;
    }

    public int chunkCount() {
        return chunks.size();
    }

    /**
     * Duration of each stage, in milliseconds. Parse includes type detection; chunk includes Block / Chunk slot processing
     */
    public record IngestionTimings(long parseMillis, long chunkMillis, long embedMillis, long indexMillis) {

        public static IngestionTimings zero() {
            return new IngestionTimings(0, 0, 0, 0);
        }
    }
}
