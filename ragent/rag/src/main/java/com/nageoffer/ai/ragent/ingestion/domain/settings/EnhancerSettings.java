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

import com.nageoffer.ai.ragent.ingestion.domain.enums.EnhanceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Enhancer settings.
 * Config of the document enhancement node: model id and the enhancement tasks
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnhancerSettings {

    /**
     * Model id used for enhancement
     */
    private String modelId;

    /**
     * Enhancement tasks to run
     */
    private List<EnhanceTask> tasks;

    /**
     * One enhancement task.
     * Type and prompt config for a single enhancement
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnhanceTask {

        /**
         * Enhancement task type
         */
        private EnhanceType type;

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
