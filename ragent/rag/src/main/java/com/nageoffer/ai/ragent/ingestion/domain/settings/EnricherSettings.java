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

import com.nageoffer.ai.ragent.ingestion.domain.enums.ChunkEnrichType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Enricher settings.
 * <p>
 * Config of the text-chunk enrichment node: model id, whether to attach document metadata, and the enrichment tasks.
 * The enricher adds extra information to chunks after splitting.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnricherSettings {

    /**
     * Model id used for enrichment
     */
    private String modelId;

    /**
     * Whether to attach document metadata to chunks
     */
    private Boolean attachDocumentMetadata;

    /**
     * Enrichment tasks to run
     */
    private List<ChunkEnrichTask> tasks;

    /**
     * One chunk enrichment task.
     * Type and prompt config for a single chunk enrichment
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChunkEnrichTask {

        /**
         * Enrichment task type
         */
        private ChunkEnrichType type;

        /**
         * System prompt
         */
        private String systemPrompt;

        /**
         * User prompt template.
         * Supports variable substitution
         */
        private String userPromptTemplate;
    }
}
