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

package com.nageoffer.ai.ragent.mcp.config.bit;

import com.nageoffer.ai.ragent.mcp.config.bit.BitSchemaInitializer.ParsedUrl;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitSchemaInitializerTest {

    private static final String URL = "jdbc:postgresql://127.0.0.1:5432/ragent_bit?client_encoding=UTF8";

    @Test
    void shouldTakeDatabaseNameOutOfUrl() {
        ParsedUrl parsed = BitSchemaInitializer.parseUrl(URL);

        assertEquals("ragent_bit", parsed.database());
        assertEquals("127.0.0.1:5432", parsed.authority());
        assertEquals(URL, parsed.url());
    }

    /**
     * Switching to the maintenance database must keep the parameters as-is: if client_encoding is dropped, the create-database connection no longer uses the same encoding as the business connection
     */
    @Test
    void shouldKeepQueryWhenSwitchingToMaintenanceDatabase() {
        assertEquals("jdbc:postgresql://127.0.0.1:5432/postgres?client_encoding=UTF8",
                BitSchemaInitializer.parseUrl(URL).withDatabase("postgres"));
    }

    @Test
    void shouldWorkWithoutPortAndQuery() {
        ParsedUrl parsed = BitSchemaInitializer.parseUrl("jdbc:postgresql://db-host/ragent_bit");

        assertEquals("ragent_bit", parsed.database());
        assertEquals("jdbc:postgresql://db-host/postgres", parsed.withDatabase("postgres"));
    }

    /**
     * The database name is spliced into CREATE DATABASE. DDL cannot use placeholders, so the value range must be locked down before it goes in
     */
    @Test
    void shouldRejectDatabaseNameThatCannotBeInterpolatedSafely() {
        assertThrows(IllegalStateException.class,
                () -> BitSchemaInitializer.parseUrl("jdbc:postgresql://127.0.0.1:5432/bit\";DROP DATABASE ragent"));
        assertThrows(IllegalStateException.class,
                () -> BitSchemaInitializer.parseUrl("jdbc:postgresql://127.0.0.1:5432/"));
    }

    @Test
    void shouldRejectUrlOfAnotherDatabaseVendor() {
        assertThrows(IllegalStateException.class,
                () -> BitSchemaInitializer.parseUrl("jdbc:mysql://127.0.0.1:3306/ragent_bit"));
        assertThrows(IllegalStateException.class,
                () -> BitSchemaInitializer.parseUrl(null));
    }

    /**
     * Missing CREATEDB permission and being unable to connect are different problems, with different next steps. Do not report a generic "initialization failed"
     */
    @Test
    void shouldTranslateFailureIntoSomethingActionable() {
        ParsedUrl parsed = BitSchemaInitializer.parseUrl(URL);

        assertTrue(BitSchemaInitializer.explain(new SQLException("denied", "42501"), parsed)
                .contains("createdb ragent_bit"));
        String unreachable = BitSchemaInitializer.explain(new SQLException("refused", "08001"), parsed);
        assertTrue(unreachable.contains("ragent.bit.datasource"));
        assertTrue(unreachable.contains(URL));
    }

    /**
     * A version mismatch does not run an incremental migration. The error must spell out the two steps to start over
     */
    @Test
    void versionMismatchMustTellHowToRecover() {
        String message = BitSchemaInitializer.versionMismatch(0, "ragent_bit");

        assertTrue(message.contains("dropdb ragent_bit"));
        assertTrue(message.contains("BizDataInitMain"));
    }
}
