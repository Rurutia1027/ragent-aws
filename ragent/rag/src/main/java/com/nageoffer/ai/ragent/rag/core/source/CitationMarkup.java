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

package com.nageoffer.ai.ragent.rag.core.source;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Pattern;

/**
 * Inline citation-marker helper for answers
 * <p>
 * Citations are persisted with the answer body so positions stay stable, but they should be removed before the next turn's model history or recommended-question generation,
 * so the previous turn's local numbers do not pollute this turn's citation numbers
 */
public final class CitationMarkup {

    /**
     * Matches the system inline citation format:
     * [1](#cite-1), [10](#cite-10)
     * <p>
     * The visible number and the anchor number are not required to match, because cleanup must tolerate a malformed marker the model sometimes emits,
     * for example [1](#cite-2) should also be removed from the next turn's context.
     */
    private static final Pattern INLINE_CITATION =
            Pattern.compile("\\[[1-9]\\d*]\\(#cite-[1-9]\\d*\\)");

    private CitationMarkup() {
    }

    public static String strip(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        return INLINE_CITATION.matcher(content).replaceAll("");
    }
}
