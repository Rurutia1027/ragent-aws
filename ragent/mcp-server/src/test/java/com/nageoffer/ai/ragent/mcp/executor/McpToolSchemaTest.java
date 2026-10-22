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

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.nageoffer.ai.ragent.mcp.executor.McpToolSchema.integer;
import static com.nageoffer.ai.ragent.mcp.executor.McpToolSchema.number;
import static com.nageoffer.ai.ragent.mcp.executor.McpToolSchema.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolSchemaTest {

    @Test
    void shouldCarryTypeDescriptionAndRequiredFlag() {
        JsonSchema schema = McpToolSchema.object()
                .required(string("roomId", "会议室ID"))
                .optional(integer("count", "条数"))
                .optional(number("amount", "金额"))
                .build();

        assertEquals("object", schema.type());
        assertEquals(List.of("roomId"), schema.required());
        assertEquals("string", property(schema, "roomId").get("type"));
        assertEquals("integer", property(schema, "count").get("type"));
        assertEquals("number", property(schema, "amount").get("type"));
        assertEquals("会议室ID", property(schema, "roomId").get("description"));
    }

    /**
     * Parameter order must be fixed once built: {@code Map.copyOf} orders by hash, and the order depends on the JVM's random SALT at startup,
     * so the schema sent to the model and the field order on the confirmation card change on every restart
     * <p>
     * Sixteen keys keep this assertion from depending on luck: with few keys the hash order often matches declaration order,
     * and in practice {@code a..h} has stayed ordered for a whole JVM, which would leave this test green even with the bug
     */
    @Test
    void shouldPreserveDeclarationOrder() {
        List<String> declared = List.of("roomId", "date", "startTime", "endTime", "attendees",
                "query", "count", "freshness", "amount", "categoryAmounts", "status", "orderNo",
                "skuCode", "reason", "keyword", "pageSize");

        McpToolSchema builder = McpToolSchema.object();
        declared.forEach(name -> builder.optional(string(name, "描述" + name)));

        assertEquals(declared, List.copyOf(builder.build().properties().keySet()));
    }

    /**
     * The server does not validate arguments. A loose schema lets undeclared fields flow straight into business code
     */
    @Test
    void shouldRejectUndeclaredProperties() {
        assertEquals(Boolean.FALSE, McpToolSchema.object()
                .required(string("id", "标识"))
                .build()
                .additionalProperties());
    }

    /**
     * Optional properties appear only when called. Do not stuff null into undeclared keys. In JSON Schema {@code "default": null}
     * and "no default" are different, and the model reads the former as "leave it blank by default"
     */
    @Test
    void shouldOmitUnsetAttributes() {
        Map<String, Object> plain = property(McpToolSchema.object()
                .optional(string("keyword", "关键词"))
                .build(), "keyword");

        assertFalse(plain.containsKey("title"));
        assertFalse(plain.containsKey("enum"));
        assertFalse(plain.containsKey("default"));
    }

    @Test
    void shouldCarryOptionalAttributes() {
        Map<String, Object> rich = property(McpToolSchema.object()
                .optional(string("status", "状态")
                        .title("持券状态")
                        .options(List.of("unused", "used"))
                        .defaultTo("unused"))
                .build(), "status");

        assertEquals("持券状态", rich.get("title"));
        assertEquals(List.of("unused", "used"), rich.get("enum"));
        assertEquals("unused", rich.get("default"));
    }

    /**
     * A duplicate name lets the later write silently overwrite the earlier one, and the tool starts with one parameter missing
     */
    @Test
    void shouldRejectDuplicateName() {
        McpToolSchema builder = McpToolSchema.object().required(string("id", "标识"));

        assertThrows(IllegalStateException.class, () -> builder.optional(integer("id", "另一个")));
    }

    /**
     * description is the model's only basis for deciding whether to fill a parameter. Leaving it empty is worse than not declaring the parameter
     */
    @Test
    void shouldRejectBlankNameOrDescription() {
        assertThrows(IllegalArgumentException.class, () -> string("id", "  "));
        assertThrows(IllegalArgumentException.class, () -> string("  ", "标识"));
    }

    /**
     * The built result must reject later mutation: the schema is a singleton built once at startup and shared for the process lifetime. One edit applies globally
     */
    @Test
    void shouldReturnImmutableSchema() {
        JsonSchema schema = McpToolSchema.object().required(string("id", "标识")).build();

        assertThrows(UnsupportedOperationException.class, () -> schema.properties().put("x", Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> schema.required().add("x"));
    }

    /**
     * Reusing the same builder to declare more must not rewrite the copy already published
     */
    @Test
    void buildShouldSnapshotCurrentState() {
        McpToolSchema builder = McpToolSchema.object().required(string("id", "标识"));
        JsonSchema first = builder.build();

        builder.optional(string("extra", "附加"));

        assertFalse(first.properties().containsKey("extra"));
        assertTrue(builder.build().properties().containsKey("extra"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> property(JsonSchema schema, String name) {
        return (Map<String, Object>) schema.properties().get(name);
    }
}
