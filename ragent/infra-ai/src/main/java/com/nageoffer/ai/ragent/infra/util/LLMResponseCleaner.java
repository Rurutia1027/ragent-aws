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

package com.nageoffer.ai.ragent.infra.util;

import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * Helpers for cleaning LLM output
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class LLMResponseCleaner {

    private static final String FENCE = "```";

    private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^```[\\w-]*\\s*\\n?");
    private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("\\n?```\\s*$");

    /**
     * Strip Markdown code fences (for example ```json ... ```)
     * <p>
     * With an opening fence, cut through the last closing fence; otherwise strip only the trailing fence
     */
    public static String stripMarkdownCodeFence(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if (!cleaned.startsWith(FENCE)) {
            return TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("").trim();
        }
        cleaned = LEADING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        int closing = cleaned.lastIndexOf(FENCE);
        return (closing < 0 ? cleaned : cleaned.substring(0, closing)).trim();
    }
}
