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

package com.nageoffer.ai.ragent.knowledge.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Document processing mode
 */
@Getter
@RequiredArgsConstructor
public enum ProcessMode {

    /**
     * Chunk-strategy mode (default)
     */
    CHUNK("chunk"),

    /**
     * Pipeline mode
     */
    PIPELINE("pipeline");

    /**
     * Processing-mode value
     */
    private final String value;

    /**
     * Resolves the enum from a value
     *
     * @param value processing-mode value
     * @return the matching enum, or null if none
     */
    public static ProcessMode fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (ProcessMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * Parses the processing mode. An empty or illegal value throws
     */
    public static ProcessMode normalize(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("Processing mode cannot be empty");
        }
        ProcessMode result = fromValue(value);
        if (result == null) {
            throw new IllegalArgumentException("Unsupported processing mode: " + value);
        }
        return result;
    }
}
