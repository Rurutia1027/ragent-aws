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

package com.nageoffer.ai.ragent.knowledge.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkBudget;
import com.nageoffer.ai.ragent.core.ingest.IngestionSpec;
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Read, write, validate, and normalize the document-level ingestion config: the single point
 * <p>
 * Ranges are guaranteed by construction of {@link ChunkBudget} and {@link ParseProfile}. This only turns a
 * construction exception into an error a user can read. A missing field always falls back to {@link IngestionSpec#defaults()},
 * the one default in the system. Whatever shape the UI submits is converged here into canonical JSON for storage, so the read path does not have to probe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionSpecCodec {

    /**
     * Wire key names of the budget fields. This class reads by them, and {@link IngestionSpecSchemaProvider} issues
     * the form by them. They must share this one set of constants. A second copy of the strings means a renamed key
     * is submitted and then silently takes the default: the UI submits as usual, the backend stores as usual, and nothing reports an error
     */
    public static final String KEY_MAX_CHARS = "maxChars";
    public static final String KEY_OVERLAP_CHARS = "overlapChars";
    public static final String KEY_ROWS_PER_CHUNK = "rowsPerChunk";
    public static final String KEY_TOLERANCE_FACTOR = "toleranceFactor";

    private static final String KEY_PARSE_PROFILE = "parseProfile";

    /**
     * No-split sentinel. Keeps the UI's existing {@code -1}. On the wire (submit, store, response, schema) this is the only representation
     * <p>
     * Inside the domain object, {@link ChunkBudget} uses {@code Integer.MAX_VALUE}. The translation between the two happens only in this class
     */
    public static final int WHOLE_DOCUMENT_SENTINEL = -1;

    private final ObjectMapper objectMapper;

    /**
     * Read: JSON in the database -> config object. Empty or corrupt always falls back to the default
     * <p>
     * Must go through {@link SpecWire}, not readValue straight into the domain object. A stored {@code maxChars} of
     * {@code -1} hits {@link ChunkBudget}'s "must be &gt; 0" check and the catch below quietly swaps in the default
     * config, so a document that should not be split is silently cut at 1024
     */
    public IngestionSpec read(String json) {
        if (!StringUtils.hasText(json)) {
            return IngestionSpec.defaults();
        }
        try {
            return objectMapper.readValue(json, SpecWire.class).toDomain();
        } catch (Exception e) {
            log.warn("摄取配置解析失败，回落默认配置：{}", json, e);
            return IngestionSpec.defaults();
        }
    }

    /**
     * Write: config object -> JSON for storage
     */
    public String write(IngestionSpec spec) {
        try {
            return objectMapper.writeValueAsString(SpecWire.of(spec == null ? IngestionSpec.defaults() : spec));
        } catch (Exception e) {
            throw new ClientException("Failed to serialize ingestion configuration");
        }
    }

    /**
     * Validates and normalizes config JSON submitted by the UI, and returns canonical JSON for storage
     *
     * @param rawJson raw JSON from the UI; nullable (empty means all defaults)
     * @return canonical JSON, or null when the input is empty (an empty column means use the default)
     */
    public String normalize(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return null;
        }
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(rawJson.trim(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new ClientException("Ingestion configuration JSON is invalid");
        }
        try {
            return write(fromMap(raw));
        } catch (IllegalArgumentException e) {
            throw new ClientException("Ingestion configuration is invalid: " + e.getMessage());
        }
    }

    private IngestionSpec fromMap(Map<String, Object> raw) {
        return IngestionSpec.of(ParseProfile.from(readString(raw, KEY_PARSE_PROFILE)),
                toBudget(readInt(raw, KEY_MAX_CHARS), readInt(raw, KEY_OVERLAP_CHARS),
                        readInt(raw, KEY_ROWS_PER_CHUNK), readInt(raw, KEY_TOLERANCE_FACTOR)));
    }

    /**
     * Four integers -> chunk budget. Sentinel translation and missing-field fallback exist only here
     * <p>
     * Both the flat keys the UI submits and the nested {@code budget} read back from storage are handled here. An old
     * {@code Integer.MAX_VALUE} also maps back to whole-document mode; that is the internal value of {@link ChunkBudget#wholeDocument()}
     */
    private static ChunkBudget toBudget(Integer maxChars, Integer overlap, Integer rows, Integer tolerance) {
        if (maxChars != null && maxChars == WHOLE_DOCUMENT_SENTINEL) {
            return ChunkBudget.wholeDocument();
        }
        ChunkBudget defaults = ChunkBudget.defaults();
        int budget = maxChars != null && maxChars > 0 ? maxChars : defaults.maxChars();
        return new ChunkBudget(
                budget,
                // Default overlap scales with chunk size. The number in the default budget is paired with 1024; copied onto a small chunk it is clamped to budget-1, and each piece advances by one character
                overlap != null && overlap >= 0 ? overlap : ChunkBudget.defaultOverlapFor(budget),
                rows != null && rows > 0 ? rows : defaults.rowsPerChunk(),
                tolerance != null && tolerance > 0 ? tolerance : defaults.toleranceFactor());
    }

    private static Integer readInt(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && StringUtils.hasText(str)) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String readString(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * Wire shape of the ingestion config. Same shape as {@link IngestionSpec}, except whole-document mode is
     * {@link #WHOLE_DOCUMENT_SENTINEL} rather than the domain's {@code Integer.MAX_VALUE}
     * <p>
     * What goes out on the wire must be the same value that came in. Serializing the domain object directly means the
     * UI submits {@code -1}, the database holds {@code 2147483647}, and the response sends that back, so reopening
     * the edit dialog shows an astronomical number in "chunk size"
     *
     * @param version      schema version; missing means the current version
     * @param parseProfile parse profile
     * @param budget       chunk budget
     */
    private record SpecWire(int version, ParseProfile parseProfile, BudgetWire budget) {

        static SpecWire of(IngestionSpec spec) {
            ChunkBudget budget = spec.budget();
            return new SpecWire(spec.version(), spec.parseProfile(), budget.isWholeDocument()
                    ? new BudgetWire(WHOLE_DOCUMENT_SENTINEL, 0, WHOLE_DOCUMENT_SENTINEL, budget.toleranceFactor())
                    : new BudgetWire(budget.maxChars(), budget.overlapChars(),
                    budget.rowsPerChunk(), budget.toleranceFactor()));
        }

        IngestionSpec toDomain() {
            return new IngestionSpec(
                    version > 0 ? version : IngestionSpec.CURRENT_VERSION,
                    parseProfile,
                    budget == null
                            ? ChunkBudget.defaults()
                            : toBudget(budget.maxChars(), budget.overlapChars(),
                            budget.rowsPerChunk(), budget.toleranceFactor()));
        }
    }

    /**
     * Wire shape of the chunk budget. Fields may be missing; a missing field always falls back to the default in {@link #toBudget}
     */
    private record BudgetWire(Integer maxChars, Integer overlapChars, Integer rowsPerChunk, Integer toleranceFactor) {
    }
}
