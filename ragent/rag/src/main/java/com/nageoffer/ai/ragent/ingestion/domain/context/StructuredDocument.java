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

package com.nageoffer.ai.ragent.ingestion.domain.context;

import com.nageoffer.ai.ragent.core.parser.model.Block;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Structured document.
 * <p>
 * A document after parsing: plain text, section structure, tables, and metadata.
 * Structured parsing keeps more of the original document structure.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructuredDocument {

    /**
     * Plain text of the document
     */
    private String text;

    /**
     * Section structure.
     * Headings and content organized by level
     */
    private List<StructuredSection> sections;

    /**
     * Tables in the document
     */
    private List<StructuredTable> tables;

    /**
     * Document metadata.
     * For example author, created time, page count
     */
    private Map<String, Object> metadata;

    /**
     * Structured Block list (introduced by the v1.1 multimodal parse change)
     * <p>
     * Produced by DocumentParser.parseStructured() and consumed by ChunkerNode.
     * The old fields {@link #sections}/{@link #tables} stay for compatibility,
     * but the new path (after M5/P1.7) treats blocks as authoritative.
     */
    private List<Block> blocks;

    /**
     * Document section.
     * One section or paragraph: title, level, content, and position in the source
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StructuredSection {

        /**
         * Section title
         */
        private String title;

        /**
         * Section level (1 is a top-level heading, 2 is the next level)
         */
        private Integer level;

        /**
         * Text of the section
         */
        private String content;

        /**
         * Start offset of the section in the original document
         */
        private Integer startOffset;

        /**
         * End offset of the section in the original document
         */
        private Integer endOffset;
    }

    /**
     * Document table.
     * One table: title, row data, and position in the source
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StructuredTable {

        /**
         * Table title or name
         */
        private String title;

        /**
         * Table rows.
         * Each row is a list of strings, one per cell
         */
        private List<List<String>> rows;

        /**
         * Start offset of the table in the original document
         */
        private Integer startOffset;

        /**
         * End offset of the table in the original document
         */
        private Integer endOffset;
    }
}
