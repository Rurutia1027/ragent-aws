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

import cn.hutool.core.util.StrUtil;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Shared argument reading and result wrapping for executors
 */
@Slf4j
public final class McpToolResults {

    /**
     * Current logged-in user passed through by the caller in {@code _meta}
     * <p>
     * The key name matches the user-identity metadata the Agent sends. The server starts on its own and does not depend on the Agent module
     */
    public static final String META_USER_ID = "com.nageoffer.ragent/userId";

    private McpToolResults() {
    }

    /**
     * Read the user id passed through by the caller. Returns null when it is missing
     * <p>
     * {@code _meta} is a client-supplied extension point, and the spec says the server must not use it for authentication decisions. This process binds only localhost
     * and serves only the same application, so using it to scope data is valid. If it is ever exposed, switch to OAuth rather than hardening this
     */
    public static String userId(CallToolRequest request) {
        Map<String, Object> meta = request == null || request.meta() == null ? Map.of() : request.meta();
        Object value = meta.get(META_USER_ID);
        return value instanceof String userId && StrUtil.isNotBlank(userId) ? StrUtil.trim(userId) : null;
    }

    /**
     * Shared rejection when a user-scoped tool cannot read an identity
     * <p>
     * Do not fall back to "query with null": that path does not error and returns empty, which looks like "you have no orders", the hardest silent failure to trace.
     * The copy does not say "log in again". The user is already logged in; the call chain is broken, and sending them to log in again only leads debugging down a dead end.
     * The real cause goes to the server log with the tool name, so "the whole chain is broken" stays separate from "this one tool omitted the identity"
     */
    public static CallToolResult identityRequired(String toolId) {
        log.warn("调用方未透传登录身份，已回绝本次调用, toolId={}", toolId);
        return error("The system cannot confirm your identity right now. Nothing was executed. Please try again later.");
    }

    /**
     * Shared exit for fallback exceptions. {@code action} is an action name such as "place order" or "order query"
     * <p>
     * Raw underlying exception text goes only to the server log: {@code e.getMessage()} holds SQL fragments, connection strings, and the top of a null-pointer stack.
     * Splicing that into the return value hands it to the model, which retells it to the user as a business conclusion. The caller needs "this attempt failed",
     * not "which layer crashed". The latter is in the log, and the log is more complete than the return value.
     * {@link McpToolException} is the exception: that copy was composed by the thrower and declared safe to show the model
     */
    public static CallToolResult failure(String action, Exception e) {
        return e instanceof McpToolException
                ? error(action + " failed: " + e.getMessage())
                : error(action + " failed. Please try again later");
    }

    public static CallToolResult success(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text)))
                .isError(false)
                .build();
    }

    public static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }

    public static Map<String, Object> args(CallToolRequest request) {
        return request.arguments() != null ? request.arguments() : Map.of();
    }

    public static LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(StrUtil.trim(value));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
