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

package com.nageoffer.ai.ragent.infra.enums;

import lombok.Getter;

/**
 * Model tier enum
 * <p>
 * A tier expresses a quality, cost, and latency budget, not a business task. The default tier is standard.
 * A call site that wants a faster or stronger model passes this enum as an override; routing then picks candidates in that tier and falls back on failure.
 * Each enum key matches a tier key under ai.chat.tiers in application.yaml
 */
@Getter
public enum Tier {

    /**
     * Fast tier: latency first, for frequent or low-risk tasks (titles, disambiguation, rewrite, summary, ingestion enrichment)
     */
    FAST("fast"),

    /**
     * Standard tier: balances quality and cost, and is the default when no tier is set
     */
    STANDARD("standard"),

    /**
     * Deep tier: high quality and high cost, for deep-thinking answers (usually triggered by thinking=true)
     */
    DEEP("deep");

    /**
     * -- GETTER --
     *  Tier key under ai.chat.tiers
     */
    private final String key;

    Tier(String key) {
        this.key = key;
    }
}
