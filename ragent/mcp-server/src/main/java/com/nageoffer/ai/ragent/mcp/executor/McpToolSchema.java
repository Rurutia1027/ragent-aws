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
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for MCP tool input schemas. Collects the {@code Map.of("type", "string", ...)} maps each executor used to assemble by hand
 * <p>
 * Covers the scalars and object arrays the tools in this repo actually use, plus title, description, default, and enum.
 */
public final class McpToolSchema {

    private final Map<String, Object> properties = new LinkedHashMap<>();

    private final List<String> required = new ArrayList<>();

    private McpToolSchema() {
    }

    public static McpToolSchema object() {
        return new McpToolSchema();
    }

    public static Property string(String name, String description) {
        return new Property(name, "string", description);
    }

    public static Property integer(String name, String description) {
        return new Property(name, "integer", description);
    }

    public static Property number(String name, String description) {
        return new Property(name, "number", description);
    }

    public static Property array(String name, String description, McpToolSchema itemSchema) {
        Property property = new Property(name, "array", description);
        property.attributes.put("items", Map.of(
                "type", "object", "properties", unmodifiableOrdered(itemSchema.properties),
                "required", List.copyOf(itemSchema.required), "additionalProperties", false));
        property.attributes.put("minItems", 1);
        return property;
    }

    /**
     * Required parameter. The name is added to the schema required list
     */
    public McpToolSchema required(Property property) {
        required.add(property.name);
        return put(property);
    }

    public McpToolSchema optional(Property property) {
        return put(property);
    }

    /**
     * {@code additionalProperties} is always false: keys outside the schema are not accepted
     * <p>
     * The server does not validate input against this (the SDK JsonSchemaValidator only covers outputSchema). This is purely a constraint sent to the model.
     * Without it the model may invent parameter names, and invented keys are silently ignored in handleCall
     */
    public JsonSchema build() {
        return new JsonSchema("object", unmodifiableOrdered(properties), List.copyOf(required),
                false, null, null);
    }

    /**
     * Read-only but order-preserving: {@code Map.copyOf} scrambles iteration order. Once parameter order is scrambled, the schema sent to the model
     * and the confirmation-card field order differ on every startup
     */
    private static Map<String, Object> unmodifiableOrdered(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private McpToolSchema put(Property property) {
        if (properties.put(property.name, property.toMap()) != null) {
            throw new IllegalStateException("Duplicate MCP tool parameter name: " + property.name);
        }
        return this;
    }

    /**
     * A single parameter. name and description are required at construction
     * <p>
     * description is the model's only basis for deciding whether to fill the parameter. Making it an optional chain step means someone will omit it
     */
    public static final class Property {

        private final String name;

        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Property(String name, String type, String description) {
            if (StrUtil.isBlank(name)) {
                throw new IllegalArgumentException("MCP tool parameter name cannot be empty");
            }
            if (StrUtil.isBlank(description)) {
                throw new IllegalArgumentException("MCP tool parameter is missing a description: " + name);
            }
            this.name = name;
            attributes.put("type", type);
            attributes.put("description", description);
        }

        /**
         * Display label. The manual confirmation card renders the field name from it, and falls back to the parameter name when it is absent
         * <p>
         * Write-tool parameters should set one: showing the user {@code receiverPhone} on the card is worse than showing "receiver phone"
         */
        public Property title(String title) {
            attributes.put("title", title);
            return this;
        }

        /**
         * Allowed values
         */
        public Property options(List<String> options) {
            attributes.put("enum", List.copyOf(options));
            return this;
        }

        public Property defaultTo(Object defaultValue) {
            attributes.put("default", defaultValue);
            return this;
        }

        private Map<String, Object> toMap() {
            return unmodifiableOrdered(attributes);
        }
    }
}
