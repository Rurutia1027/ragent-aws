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
 * An orderable configuration plus display fields of the style it belongs to, so the executor does not query the style again
 */
@Data
public class SkuDetailResult {

    private String skuCode;

    private String spuCode;

    private String spuName;

    private String name;

    private String category;

    private String subCategory;

    /**
     * Raw spec key-value JSON. Display order is fixed by BitToolSupport.specs
     */
    private String specs;

    /**
     * Raw shared-attribute JSON of the parent style. The configuration does not store it again
     */
    private String spuSpecs;

    private BigDecimal price;

    private Integer stock;

    private String status;
}
