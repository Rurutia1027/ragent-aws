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

package com.nageoffer.ai.ragent.ingestion.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Document enhancement type.
 * How the whole document is enhanced, to improve retrieval and understanding.
 * Values use lower snake_case, such as context_enhance, keywords
 */
@Getter
@RequiredArgsConstructor
public enum EnhanceType {

    /**
     * Context enhancement: add context to the text so it is easier to understand
     */
    CONTEXT_ENHANCE("context_enhance"),

    /**
     * Keyword extraction: extract important keywords from the document
     */
    KEYWORDS("keywords"),

    /**
     * Question generation: generate related questions from the document
     */
    QUESTIONS("questions"),

    /**
     * Metadata extraction: extract document metadata
     */
    METADATA("metadata");

    /**
     * Type value (lower snake_case)
     */
    private final String value;

    /**
     * Resolves the type from a string value
     */
    @JsonCreator
    public static EnhanceType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        for (EnhanceType type : values()) {
            if (type.value.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown enhance type: " + value);
    }

    private static String normalize(String value) {
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase();
        return lower.replace('-', '_');
    }

    /**
     * Returns the serialized value
     */
    @JsonValue
    public String getValue() {
        return value;
    }
}
