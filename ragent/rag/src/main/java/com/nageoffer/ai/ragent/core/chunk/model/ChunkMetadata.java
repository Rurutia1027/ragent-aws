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

package com.nageoffer.ai.ragent.core.chunk.model;

import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import lombok.Builder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured metadata for a chunk
 * <p>
 * {@link #toMap()} is the only metadata serialization point in the system. The relational store, vector store,
 * keyword index, and graph store all call it, so a new field is added in one place.
 * Keys are snake_case, matching vector-store metadata and ES index fields. Document-level keys
 * ({@code doc_id} / {@code chunk_index}) are not intrinsic to the chunk; each sink fills them in
 *
 * @param outlinePath outline path; Excel sheet names go here too
 * @param provenance  original source (file, sheet)
 * @param extras      open extension slot: chunk enrichments (summary, keywords) and caller-injected document metadata
 */
@Builder
public record ChunkMetadata(
        List<String> outlinePath,
        List<AssetRef> assets,
        Provenance provenance,
        Map<String, Object> extras
) {

    public static final String KEY_ASSETS = "assets";
    public static final String KEY_SOURCE_FILE = "source_file";
    public static final String KEY_SHEET_NAME = "sheet_name";

    public ChunkMetadata {
        outlinePath = immutableCopy(outlinePath);
        assets = immutableCopy(assets);
        extras = extras == null || extras.isEmpty() ? Map.of() : Map.copyOf(extras);
    }

    /**
     * Empty metadata: tests, and cases that truly have no structural information
     */
    public static ChunkMetadata empty() {
        return new ChunkMetadata(List.of(), List.of(), null, Map.of());
    }

    /**
     * Merges the extension slot and returns a new object, for chunk enrichments (summary / keywords) and document metadata
     */
    public ChunkMetadata withExtras(Map<String, Object> additional) {
        if (additional == null || additional.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(extras);
        merged.putAll(additional);
        return new ChunkMetadata(outlinePath, assets, provenance, merged);
    }

    /**
     * Flattens to a Map shared by every index backend. Nulls are omitted; vector JSONB and ES documents read keys as needed
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        // Write the extension slot first, then structured keys: structured facts win, and enrichments must not overwrite them
        map.putAll(extras);
        if (!assets.isEmpty()) {
            List<Map<String, Object>> assetMaps = new ArrayList<>(assets.size());
            for (AssetRef asset : assets) {
                Map<String, Object> one = new LinkedHashMap<>();
                putIfPresent(one, "url", asset.publicUrl());
                putIfPresent(one, "mime", asset.mime());
                assetMaps.add(one);
            }
            map.put(KEY_ASSETS, assetMaps);
        }
        if (provenance != null) {
            putIfPresent(map, KEY_SOURCE_FILE, provenance.sourceFile());
            putIfPresent(map, KEY_SHEET_NAME, provenance.sheetName());
        }
        return map;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return source == null || source.isEmpty() ? List.of() : List.copyOf(source);
    }
}
