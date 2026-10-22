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

import java.util.List;
import java.util.Map;

/**
 * Parser settings.
 * Config of the document parse node, including the parse rules
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParserSettings {

    /**
     * Parse rules.
     * Match a parser by MIME type
     */
    private List<ParserRule> rules;

    /**
     * One parse rule.
     * Which MIME types should use which parser
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParserRule {

        /**
         * Document type.
         * For example PDF, WORD, MARKDOWN
         */
        private String mimeType;

        /**
         * Extra options for the parser
         */
        private Map<String, Object> options;
    }
}
