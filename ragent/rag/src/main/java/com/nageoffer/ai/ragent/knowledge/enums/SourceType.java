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
 * Document source type
 */
@Getter
@RequiredArgsConstructor
public enum SourceType {

    /**
     * Local file upload
     */
    FILE("file"),

    /**
     * Remote URL
     */
    URL("url");

    /**
     * Source type value
     */
    private final String value;

    /**
     * Resolves the enum from a value
     *
     * @param value source type value
     * @return the matching enum, or null if none
     */
    public static SourceType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        // Accept several aliases for a file type
        if ("file".equals(normalized) || "localfile".equals(normalized) || "local_file".equals(normalized)) {
            return FILE;
        }
        if ("url".equals(normalized)) {
            return URL;
        }
        return null;
    }

    /**
     * Parses the source type. An empty or illegal value throws
     */
    public static SourceType normalize(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("Source type cannot be empty");
        }
        SourceType result = fromValue(value);
        if (result == null) {
            throw new IllegalArgumentException("Unsupported source type: " + value);
        }
        return result;
    }
}
