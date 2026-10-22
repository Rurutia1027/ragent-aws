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

import com.nageoffer.ai.ragent.mcp.dao.result.HeldCouponResult;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.Map;

/**
 * Coupon decision rules
 * <p>
 * The query tool's trial calculation and the real deduction at order time share this copy. Two copies would drift into
 * "the query says it can be used, the order says it cannot", and that mismatch is the hardest to trace
 */
final class BitCouponRules {

    static final String STATUS_UNUSED = "未使用";
    static final String STATUS_USED = "已使用";
    static final String STATUS_EXPIRED = "已过期";

    static final String ALL_CATEGORY = "全品类";

    private static final String TYPE_DISCOUNT = "折扣";

    private BitCouponRules() {
    }

    /**
     * The stored status only says whether it has been used. Expiry is always judged from the current time
     */
    static String effectiveStatus(HeldCouponResult coupon) {
        if (STATUS_USED.equals(coupon.getHeldStatus())) {
            return STATUS_USED;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return coupon.getValidTo() != null && coupon.getValidTo().before(now) ? STATUS_EXPIRED : STATUS_UNUSED;
    }

    /**
     * The threshold is judged on the category subtotal. Only when that subtotal is missing does it fall back to the grand total, and the copy says so
     * <p>
     * Holding status and validity are judged again here: placing an order takes this path, and it cannot assume the caller already filtered
     */
    static Verdict judge(HeldCouponResult coupon, BigDecimal amount, Map<String, BigDecimal> categoryAmounts) {
        String effective = effectiveStatus(coupon);
        if (!STATUS_UNUSED.equals(effective)) {
            return new Verdict(false, BigDecimal.ZERO, String.format("不可用，该券%s", effective));
        }
        if (coupon.getValidFrom() != null && coupon.getValidFrom().after(new Timestamp(System.currentTimeMillis()))) {
            return new Verdict(false, BigDecimal.ZERO,
                    String.format("不可用，该券 %s 起生效", BitToolSupport.date(coupon.getValidFrom())));
        }

        BigDecimal base = amount;
        String scope = "合计金额";
        if (coupon.getCategory() != null) {
            if (!categoryAmounts.isEmpty() && !categoryAmounts.containsKey(coupon.getCategory())) {
                return new Verdict(false, BigDecimal.ZERO,
                        String.format("不可用，本单没有%s类商品", coupon.getCategory()));
            }
            BigDecimal categoryAmount = categoryAmounts.get(coupon.getCategory());
            if (categoryAmount != null) {
                base = categoryAmount;
                scope = coupon.getCategory() + "小计";
            } else {
                scope = "合计金额（未提供" + coupon.getCategory() + "小计，结果仅供参考）";
            }
        }

        BigDecimal threshold = coupon.getThreshold() == null ? BigDecimal.ZERO : coupon.getThreshold();
        if (base.compareTo(threshold) < 0) {
            return new Verdict(false, BigDecimal.ZERO,
                    String.format("不可用，%s %s 未达门槛 %s，还差 %s",
                            scope, BitToolSupport.money(base), BitToolSupport.money(threshold),
                            BitToolSupport.money(threshold.subtract(base))));
        }

        // Discount applies to base: a category discount coupon should discount only that category. A threshold-minus coupon reduces the whole order, so it is capped at the total
        BigDecimal discount = TYPE_DISCOUNT.equals(coupon.getType())
                ? base.multiply(BigDecimal.ONE.subtract(coupon.getDiscountValue())).setScale(2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue().min(amount);
        return new Verdict(true, discount,
                String.format("可用，按%s判定，可抵扣 %s", scope, BitToolSupport.money(discount)));
    }

    static String rule(HeldCouponResult coupon) {
        String threshold = coupon.getThreshold() == null || coupon.getThreshold().signum() <= 0
                ? "无门槛"
                : "满 " + BitToolSupport.money(coupon.getThreshold());
        return TYPE_DISCOUNT.equals(coupon.getType())
                ? String.format("%s，按 %s 折", threshold, discountLabel(coupon.getDiscountValue()))
                : String.format("%s，减 %s", threshold, BitToolSupport.money(coupon.getDiscountValue()));
    }

    /**
     * Read 0.90 as "nine", not "0.90", so a model that copies it out does not say "0.9 off"
     */
    static String discountLabel(BigDecimal discountValue) {
        BigDecimal tenth = discountValue.multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP);
        return tenth.stripTrailingZeros().toPlainString();
    }


    record Verdict(boolean usable, BigDecimal discount, String reason) {
    }
}
