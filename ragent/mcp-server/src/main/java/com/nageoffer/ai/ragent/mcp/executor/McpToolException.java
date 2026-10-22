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

/**
 * Tool exception whose message may be returned to the model as-is
 * <p>
 * Only the line that throws an exception knows whether its message is safe for the model: a self-composed "upstream returned 503" is safe,
 * while a library exception may carry a SQL fragment or a connection string. Making that a type lets the thrower declare it once.
 * {@link McpToolResults#failure} follows the declaration, and the catch site does not have to guess
 */
public class McpToolException extends RuntimeException {

    public McpToolException(String message) {
        super(message);
    }
}
