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

package com.nageoffer.ai.ragent.rag.core.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Vector retrieval request:
 * - basic query + topK
 * - optional Milvus collectionName
 * - simple metadata equality filters (for extension)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrieveRequest {

    /**
     * User question in natural language / query text
     */
    private String query;

    /**
     * TopK to return; default 5
     */
    @Builder.Default
    private int topK = 5;

    /**
     * Target vector collection name:
     * - empty: use the default collection
     * - non-empty: search the named collection
     */
    private String collectionName;

    /**
     * Target logical collection list.
     * Multiple values filter by those collections in one search; topK is the total budget for the whole filter range
     */
    private List<String> collectionNames;

    /**
     * Metadata equality filters (extension):
     * - key is the metadata field name
     * - value is the match value
     * The implementation may build a Milvus expr from the map (AND-combined).
     * <p>
     * Example:
     * {"biz_type": "ATTENDANCE", "env": "TEST"}
     */
    private Map<String, Object> metadataFilters;

    /**
     * The new multi-collection parameter takes precedence; the old single-collection parameter stays for existing callers
     */
    public List<String> getEffectiveCollectionNames() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (collectionNames != null) {
            collectionNames.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty() && collectionName != null && !collectionName.isBlank()) {
            normalized.add(collectionName.trim());
        }
        return List.copyOf(normalized);
    }
}
