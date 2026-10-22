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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Ingestion node type.
 * Node types supported by a document ingestion pipeline.
 * Values use lower snake_case, such as fetcher, parser, chunker
 */
@Getter
@RequiredArgsConstructor
public enum IngestionNodeType {

    /**
     * Fetch node: load a document from a source
     */
    FETCHER("fetcher"),

    /**
     * Parse node: turn document content into text
     */
    PARSER("parser"),

    /**
     * Enhance node: AI enhancement of the whole document
     */
    ENHANCER("enhancer"),

    /**
     * Chunk node: split the document into text chunks
     */
    CHUNKER("chunker"),

    /**
     * Chunk-enrich node: AI enhancement of each text chunk
     */
    ENRICHER("enricher"),

    /**
     * Index node: embed text chunks and store them in the vector database
     */
    INDEXER("indexer");

    /**
     * String value of the node type (lower snake_case)
     */
    private final String value;

    /**
     * Resolves the enum from a string value
     */
    public static IngestionNodeType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        for (IngestionNodeType type : values()) {
            if (type.value.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + value);
    }

    private static String normalize(String value) {
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase();
        return lower.replace('-', '_');
    }
}
