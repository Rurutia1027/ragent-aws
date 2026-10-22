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

package com.nageoffer.ai.ragent.rag.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Intent kind
 * Distinguishes kinds of user intent
 */
@Getter
@RequiredArgsConstructor
public enum IntentKind {

    /**
     * Knowledge base; goes through RAG
     */
    KB(0),

    /**
     * System interaction, such as a greeting or a self-introduction
     */
    SYSTEM(1),

    /**
     * MCP, live data interaction
     */
    MCP(2);

    /**
     * Intent kind code
     */
    private final int code;

    /**
     * Resolve an intent kind from its code
     *
     * @param code intent kind code
     * @return matching intent kind, or null if the code is unknown
     */
    public static IntentKind fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (IntentKind e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }

    /**
     * Return the enum name
     *
     * @return enum name
     */
    @Override
    public String toString() {
        return name();
    }
}