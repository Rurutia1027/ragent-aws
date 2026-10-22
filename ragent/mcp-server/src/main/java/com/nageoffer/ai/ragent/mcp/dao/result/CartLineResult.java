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

package com.nageoffer.ai.ragent.mcp.dao.result;

import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * One cart line, with the product's current price, stock, and category
 * <p>
 * Both the add-to-cart price and the current price must be present: a drop, a rise, or a stock-out has to be said before checkout
 */
@Data
public class CartLineResult {

    private Long id;

    private String userId;

    private String skuCode;

    private String skuName;

    private String category;

    private Integer quantity;

    /**
     * Snapshot of the price when added. Compared with the current price to find discounted items
     */
    private BigDecimal addedPrice;

    private BigDecimal price;

    private Integer stock;

    private String status;

    /**
     * Time added to the cart
     */
    private Timestamp createTime;

    private Timestamp updateTime;
}
