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

package com.nageoffer.ai.ragent.mcp.config;

import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

/**
 * Predefined MCP tool annotation constants, read and write
 */
public final class McpToolAnnotations {

    /**
     * Read-only tool: changes no data, and repeated calls are equivalent
     */
    public static final ToolAnnotations READ_ONLY = new ToolAnnotations(null, true, false, true, false, null);

    /**
     * Write tool: has a real business side effect, and repeated calls are not equivalent
     */
    public static final ToolAnnotations WRITE = new ToolAnnotations(null, false, false, false, false, null);

    private McpToolAnnotations() {
    }
}
