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

package com.nageoffer.ai.ragent.core.ingest;

import com.nageoffer.ai.ragent.core.chunk.model.ChunkBudget;
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;

/**
 * Document-level ingestion config (L3): how this document is parsed and cut, stored in the JSONB column {@code t_knowledge_document.ingestion_spec}
 * <p>
 * Does not include embeddingModel. The embedding model is a knowledge-base (L2) constraint. A document may not
 * override it; only {@link VectorTarget} supplies it
 *
 * @param version      schema version, so a future change can recognize an old value
 * @param parseProfile parse profile
 * @param budget       chunk budget
 */
public record IngestionSpec(int version, ParseProfile parseProfile, ChunkBudget budget) {

    public static final int CURRENT_VERSION = 2;

    public IngestionSpec {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be > 0, actual " + version);
        }
        parseProfile = parseProfile == null ? ParseProfile.defaultProfile() : parseProfile;
        budget = budget == null ? ChunkBudget.defaults() : budget;
    }

    public static IngestionSpec defaults() {
        return new IngestionSpec(CURRENT_VERSION, ParseProfile.defaultProfile(), ChunkBudget.defaults());
    }

    public static IngestionSpec of(ParseProfile parseProfile, ChunkBudget budget) {
        return new IngestionSpec(CURRENT_VERSION, parseProfile, budget);
    }
}
