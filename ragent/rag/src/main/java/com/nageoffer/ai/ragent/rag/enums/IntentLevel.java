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
 * Intent level
 * The level of an intent in the knowledge base
 */
@Getter
@RequiredArgsConstructor
public enum IntentLevel {

    /**
     * Top level: group IT / business systems / middleware environment
     */
    DOMAIN(0),

    /**
     * Second level: HR / admin / OA / Redis ...
     */
    CATEGORY(1),

    /**
     * Third level: a more specific topic, such as system overview / data security / architecture
     */
    TOPIC(2);

    private final int code;

    /**
     * Resolve an intent level from its code
     *
     * @param code level code
     * @return matching IntentLevel, or null if code is null or unknown
     */
    public static IntentLevel fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (IntentLevel e : values()) {
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
