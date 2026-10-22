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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkBudget;
import com.nageoffer.ai.ragent.core.ingest.IngestionSpec;
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ingestion config read/write: watch the round trip of "do not chunk the whole document"
 * <p>
 * Sentinels on this path have two representations — {@code -1} on the wire and {@code Integer.MAX_VALUE} inside the domain. Translation is allowed
 * only inside the codec. Miss one direction and the symptom is not an error: the UI shows 2147483647
 */
class IngestionSpecCodecTest {

    private final IngestionSpecCodec codec = new IngestionSpecCodec(new ObjectMapper());

    @Test
    void wholeDocumentSurvivesSubmitStoreReloadRoundTrip() {
        String stored = codec.normalize(
                "{\"parseProfile\":\"fidelity\",\"maxChars\":-1,\"overlapChars\":128,\"rowsPerChunk\":50}");

        // The stored shape must still be -1: the output is sent back to the frontend as-is, and the frontend only recognizes that value
        assertTrue(stored.contains("\"maxChars\":-1"), stored);
        assertFalse(stored.contains(String.valueOf(Integer.MAX_VALUE)), stored);

        IngestionSpec reloaded = codec.read(stored);
        assertTrue(reloaded.budget().isWholeDocument());
        assertEquals(ParseProfile.FIDELITY, reloaded.parseProfile());
    }

    @Test
    void legacyMaxValueStillReadsAsWholeDocument() {
        // An old build serialized the domain sentinel straight into the database
        String legacy = "{\"version\":2,\"parseProfile\":\"fast\","
                + "\"budget\":{\"maxChars\":2147483647,\"overlapChars\":0,\"rowsPerChunk\":2147483647}}";

        assertTrue(codec.read(legacy).budget().isWholeDocument());
        // An old row displays correctly without a data rewrite: rewriting once on the way out converges to the wire sentinel
        assertTrue(codec.write(codec.read(legacy)).contains("\"maxChars\":-1"));
    }

    @Test
    void budgetRoundTripsAndMissingFieldsFallBackToDefaults() {
        IngestionSpec spec = codec.read(codec.normalize("{\"maxChars\":600}"));

        ChunkBudget defaults = ChunkBudget.defaults();
        assertEquals(600, spec.budget().maxChars());
        // Default overlap follows chunk size proportionally, rather than copying the 128 that the default budget configures for 1024
        assertEquals(75, spec.budget().overlapChars());
        assertEquals(defaults.rowsPerChunk(), spec.budget().rowsPerChunk());
        assertEquals(ParseProfile.FAST, spec.parseProfile());
    }

    @Test
    void illegalOverlapIsRejectedRatherThanSilentlyFixed() {
        assertThrows(ClientException.class,
                () -> codec.normalize("{\"maxChars\":500,\"overlapChars\":500}"));
    }

    /**
     * The cap was published (the schema is sent to the frontend), so it must actually reject
     * <p>
     * If it is only a hint number, a hundred-thousand-character chunk can be stored and explode only at embedding. Whole-document mode exempts both caps, covered by
     * {@link #wholeDocumentSurvivesSubmitStoreReloadRoundTrip}
     */
    @Test
    void overLimitBudgetIsRejected() {
        assertThrows(ClientException.class,
                () -> codec.normalize("{\"maxChars\":" + (ChunkBudget.MAX_CHARS_LIMIT + 1) + "}"));
        assertThrows(ClientException.class,
                () -> codec.normalize("{\"rowsPerChunk\":" + (ChunkBudget.ROWS_PER_CHUNK_LIMIT + 1) + "}"));
    }
}
