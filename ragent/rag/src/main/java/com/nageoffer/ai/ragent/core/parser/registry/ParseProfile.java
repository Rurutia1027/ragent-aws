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

package com.nageoffer.ai.ragent.core.parser.registry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Parse profile: how much fidelity you are willing to pay for. Orthogonal to format, which MIME alone determines
 * <p>
 * The enum names are engine words, not user-facing words. In the UI these two profiles are called "regular table /
 * complex table", and that copy is issued from the single point {@code IngestionSpecSchemaProvider}
 */
public enum ParseProfile {

    /**
     * Fast profile: prefer a local parser, zero external cost. Also the global fallback: when the requested profile
     * matches nothing, fall back here, and if that still matches nothing, fail explicitly
     */
    FAST("fast"),

    /**
     * Fidelity profile: pay an external service for layout fidelity, for example Excel going through MinerU layout parsing
     */
    FIDELITY("fidelity");

    private final String code;

    ParseProfile(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static ParseProfile defaultProfile() {
        return FAST;
    }

    /**
     * Lenient parse: an empty value falls back to the default profile. An unrecognized value is an error, not a silent fallback
     */
    @JsonCreator
    public static ParseProfile from(String code) {
        if (code == null || code.isBlank()) {
            return defaultProfile();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (ParseProfile profile : values()) {
            if (profile.code.equals(normalized)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown parser tier: " + code);
    }
}
