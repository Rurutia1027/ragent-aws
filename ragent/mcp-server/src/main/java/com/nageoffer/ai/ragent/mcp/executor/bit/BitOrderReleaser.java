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

import com.nageoffer.ai.ragent.mcp.dao.mapper.OrderMapper;
import com.nageoffer.ai.ragent.mcp.dao.mapper.ProductSkuMapper;
import com.nageoffer.ai.ragent.mcp.dao.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancel an order and return the stock and coupon it holds
 * <p>
 * User cancel and timeout release share this method. Two copies would drift,
 * and the symptom of that drift is "sometimes the coupon comes back, sometimes it does not", which is the hardest to trace
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BitOrderReleaser {

    static final String STATUS_PENDING = "待支付";
    static final String STATUS_PAID = "已支付待发货";
    static final String STATUS_CANCELLED = "已取消";

    private final OrderMapper orderMapper;
    private final ProductSkuMapper productSkuMapper;
    private final UserCouponMapper userCouponMapper;
    private final TransactionTemplate bitTransactionTemplate;

    /**
     * Conditional cancel with an expected status: if the row cannot be claimed, do nothing
     * <p>
     * A user clicking pay and the timeout scan can collide. Whoever gets 1 affected row wins.
     * The loser returns false and says what happened, instead of throwing
     */
    public boolean release(String orderNo, String expectedStatus) {
        return Boolean.TRUE.equals(bitTransactionTemplate.execute(status -> {
            int cancelled = orderMapper.cancel(orderNo, expectedStatus);
            if (cancelled == 0) {
                return false;
            }
            int stockRows = productSkuMapper.restoreStockByOrder(orderNo);
            int couponRows = userCouponMapper.restoreByOrder(orderNo);
            log.info("订单已取消并释放资源, orderNo={}, from={}, 回补商品={}, 退回券={}",
                    orderNo, expectedStatus, stockRows, couponRows);
            return true;
        }));
    }
}
