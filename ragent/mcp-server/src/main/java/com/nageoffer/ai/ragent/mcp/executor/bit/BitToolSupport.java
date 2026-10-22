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

package com.nageoffer.ai.ragent.mcp.executor.bit;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.mcp.executor.McpToolResults;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared masking, formatting, and argument reading for BitSelect executors
 */
final class BitToolSupport {

    static final String EMPTY_FIELD = "-";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Comma, enumeration comma, Chinese comma, and whitespace are all separators. Whichever the model writes must be accepted
     */
    private static final String CSV_SEPARATOR = "[,，、\\s]+";

    /**
     * Closing characters recognized when cutting an address down to district. 「区」 comes before 「市」 because the cut wants the district, not the city
     */
    private static final List<String> DISTRICT_SUFFIXES = List.of("区", "县", "旗", "市");

    private BitToolSupport() {
    }

    /**
     * Unauthorized access and a missing row return the same sentence. Otherwise that sentence itself is an existence probe
     * <p>
     * This is not isError: not found is a legitimate answer. Marking it as an error makes the upper layer retry it as a tool failure
     */
    static CallToolResult notFound(String subject) {
        return McpToolResults.success("Not found: " + subject);
    }

    /**
     * Rejection for a write tool: this write did not happen, and it is always isError
     * <p>
     * The opposite of notFound on a read-only tool. There, not found is a legitimate answer. Here, "it was not written" is a fact the caller must know.
     * With this, success from a write tool means the database really changed. Unauthorized and missing still share the same copy
     */
    static CallToolResult rejected(String message) {
        return McpToolResults.error(message);
    }

    /**
     * Keep the first 3 and last 4 digits of a phone. An unrecognized short number falls back to keeping only the ends. An unfamiliar format is not a reason to emit it raw
     */
    static String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return EMPTY_FIELD;
        }
        String trimmed = StrUtil.trim(phone);
        return trimmed.length() > 7
                ? StrUtil.hide(trimmed, 3, trimmed.length() - 4)
                : StrUtil.hide(trimmed, 1, Math.max(1, trimmed.length() - 1));
    }

    /**
     * An address goes only down to district, using the first closing character, not the last
     * <p>
     * In 「浦东新区张江高科技园区」 the last 「区」 is already as specific as the park. Cutting at the last one is the same as not masking
     */
    static String maskAddress(String address) {
        if (StrUtil.isBlank(address)) {
            return EMPTY_FIELD;
        }
        String trimmed = StrUtil.trim(address);
        for (String suffix : DISTRICT_SUFFIXES) {
            int index = trimmed.indexOf(suffix);
            if (index > 0) {
                return trimmed.substring(0, index + suffix.length());
            }
        }
        return trimmed.length() > 6 ? trimmed.substring(0, 6) : trimmed;
    }

    static String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * An amount that cannot be parsed is treated as "not given". Do not fall back to 0. That would mark a coupon that meets the threshold as unusable
     */
    static BigDecimal decimal(String value) {
        String trimmed = StrUtil.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String dateTime(Timestamp value) {
        return value == null ? EMPTY_FIELD : DATE_TIME.format(value.toLocalDateTime());
    }

    static String date(Timestamp value) {
        return value == null ? EMPTY_FIELD : value.toLocalDateTime().toLocalDate().toString();
    }

    /**
     * How many days ago. Window checks such as the seven-day window are computed from this
     */
    static long daysSince(Timestamp value) {
        return value == null ? -1 : Duration.between(value.toInstant(), Instant.now()).toDays();
    }

    static List<String> csv(String value) {
        if (StrUtil.isBlank(value)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String piece : value.split(CSV_SEPARATOR)) {
            String trimmed = StrUtil.trim(piece);
            if (StrUtil.isNotBlank(trimmed) && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * The returned row count has a cap. A model passing 10000 must not read out the whole table
     */
    static int limit(Integer requested, int defaultValue, int max) {
        return requested == null || requested <= 0 ? defaultValue : Math.min(requested, max);
    }

    /**
     * On sale with zero stock, and only a few left, both have to be said. Otherwise "can the cart item still be bought" cannot be answered
     */
    /**
     * Spec display order is independent of write order. jsonb reorders by key length and dictionary order. Reading in that order
     * becomes "capacity / size / color", and configurations of the same style no longer line up
     */
    private static final List<String> SPEC_ORDER = List.of("容量", "颜色", "尺寸", "表壳尺寸", "表壳材质",
            "连接", "芯片", "核心配置", "面板", "支架", "充电盒", "包装规格", "屏幕");

    /**
     * Format spec JSON as "capacity 256GB / color black"
     */
    static String specs(String json) {
        if (StrUtil.isBlank(json)) {
            return EMPTY_FIELD;
        }
        JSONObject obj = JSONUtil.parseObj(json);
        if (obj.isEmpty()) {
            return EMPTY_FIELD;
        }
        List<String> parts = new ArrayList<>();
        for (String key : SPEC_ORDER) {
            Object value = obj.get(key);
            if (value != null) {
                parts.add(key + " " + value);
            }
        }
        // Keys missing from the order table must still appear. Otherwise a newly added dimension disappears silently
        obj.forEach((key, value) -> {
            if (!SPEC_ORDER.contains(key)) {
                parts.add(key + " " + value);
            }
        });
        return String.join(" / ", parts);
    }

    static String stockLabel(String status, int stock) {
        if (!"在售".equals(status)) {
            return status;
        }
        if (stock <= 0) {
            return "在售缺货";
        }
        return stock <= 5 ? "仅剩 " + stock + " 件" : "有货 " + stock + " 件";
    }
}
