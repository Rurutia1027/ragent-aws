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

/**
 * Browse result aggregated by style
 * <p>
 * Price range, total stock, and configuration count are aggregated from orderable configurations and are not columns of the product-style table,
 * so they get their own result object instead of being stuffed back into ProductDO. Stuffing them back would make that class a half-finished object that sometimes has values and sometimes does not
 */
@Data
public class ProductSummaryResult {

    private String spuCode;

    private String name;

    private String category;

    private String subCategory;

    private String brand;

    private String brandOwner;

    /**
     * Raw style-level shared-attribute JSON
     */
    private String specs;

    private String tags;

    private String sellingPoint;

    /**
     * Lowest price inside the filter
     */
    private BigDecimal minPrice;

    /**
     * Highest price inside the filter
     */
    private BigDecimal maxPrice;

    private Integer stock;

    private Integer skuCount;
}
