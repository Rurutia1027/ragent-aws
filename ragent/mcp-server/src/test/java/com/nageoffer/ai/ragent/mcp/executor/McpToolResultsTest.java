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

package com.nageoffer.ai.ragent.mcp.executor;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolResultsTest {

    private static final String TOOL_ID = "query_order";

    /**
     * A reverse-DNS key prefix is required by the spec, and must match the caller's constant character for character. Changing one side breaks the link
     */
    @Test
    void userIdKeyMustStayInSyncWithCaller() {
        assertEquals("com.nageoffer.ragent/userId", McpToolResults.META_USER_ID);
    }

    @Test
    void shouldReadUserIdFromMeta() {
        assertEquals("2001", McpToolResults.userId(request(Map.of(McpToolResults.META_USER_ID, "2001"))));
    }

    /**
     * Anything unreadable is null: missing, empty string, non-string, or a bare userId key are the same thing for a user-scoped tool
     */
    @Test
    void shouldReturnNullWhenIdentityIsUnusable() {
        assertNull(McpToolResults.userId(request(null)));
        assertNull(McpToolResults.userId(request(Map.of())));
        assertNull(McpToolResults.userId(request(Map.of(McpToolResults.META_USER_ID, "   "))));
        assertNull(McpToolResults.userId(request(Map.of(McpToolResults.META_USER_ID, 2001))));
        assertNull(McpToolResults.userId(request(Map.of("userId", "2001"))));
        assertNull(McpToolResults.userId(null));
    }

    /**
     * A rejection must go through isError. Otherwise the model treats the sentence as a normal business result and retells it as "you have no orders"
     */
    @Test
    void identityRequiredMustBeAnError() {
        CallToolResult result = McpToolResults.identityRequired(TOOL_ID);

        assertTrue(result.isError());
        assertTrue(((TextContent) result.content().get(0)).text().contains("identity"));
    }

    /**
     * Do not phrase this as "please log in again": the user is already logged in. The call chain is broken, and logging in again does not fix it. It only sends debugging down a dead end
     */
    @Test
    void identityRequiredMustNotBlameTheUser() {
        String text = ((TextContent) McpToolResults.identityRequired(TOOL_ID).content().get(0)).text();

        assertFalse(text.contains("登录"));
        assertFalse(text.toLowerCase().contains("log in"));
        assertFalse(text.toLowerCase().contains("sign in"));
    }

    /**
     * Not one character of the underlying exception text may enter the return value: the return value is for the model, and the model will retell SQL fragments and connection strings
     * to the user as business conclusions
     */
    @Test
    void failureMustNotLeakUnderlyingMessage() {
        String secret = "ERROR: relation \"t_order\" does not exist, jdbc:postgresql://10.0.0.7:5432/bit";

        CallToolResult result = McpToolResults.failure("Order lookup", new IllegalStateException(secret));

        String text = ((TextContent) result.content().get(0)).text();
        assertTrue(result.isError());
        assertTrue(text.contains("Order lookup failed"));
        assertFalse(text.contains("t_order"));
        assertFalse(text.contains("10.0.0.7"));
    }

    /**
     * An empty message and the exception's own class name also count as raw text: when {@code getMessage()} is null, do not fall back to splicing in toString
     */
    @Test
    void failureMustNotLeakExceptionType() {
        String text = ((TextContent) McpToolResults.failure("Place order", new NullPointerException())
                .content().get(0)).text();

        assertFalse(text.contains("NullPointerException"));
        assertFalse(text.contains("null"));
    }

    /**
     * {@link McpToolException} is copy the thrower composed and has declared safe for the model. The status code it carries is how the model
     * tells "rate-limited, retry later" from "auth is broken, do not retry". Stripping it makes the model guess
     */
    @Test
    void failureMustPassThroughDeclaredSafeMessage() {
        CallToolResult result = McpToolResults.failure("Search", new McpToolException("上游返回异常状态码: 429"));

        String text = ((TextContent) result.content().get(0)).text();
        assertTrue(result.isError());
        assertTrue(text.contains("429"));
    }

    private CallToolRequest request(Map<String, Object> meta) {
        return new CallToolRequest(TOOL_ID, new HashMap<>(), meta);
    }
}
