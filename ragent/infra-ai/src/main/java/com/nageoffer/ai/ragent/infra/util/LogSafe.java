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

/**
 * Logging safety helpers
 * <p>
 * Truncate raw responses that may be long or contain user or tool parameters before logging, to limit log volume and avoid leaking secrets in full
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class LogSafe {

    /**
     * Default preview length
     */
    private static final int DEFAULT_MAX = 500;

    /**
     * Truncate to the default length
     */
    public static String preview(String raw) {
        return preview(raw, DEFAULT_MAX);
    }

    /**
     * Truncate raw text to max characters. The overflow is replaced by an ellipsis and a total-length hint
     */
    public static String preview(String raw, int max) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= max) {
            return raw;
        }
        return raw.substring(0, max) + "...(truncated, total " + raw.length() + " chars)";
    }
}
