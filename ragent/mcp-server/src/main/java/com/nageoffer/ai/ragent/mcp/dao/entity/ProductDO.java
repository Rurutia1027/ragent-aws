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

package com.nageoffer.ai.ragent.mcp.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nageoffer.ai.ragent.mcp.dao.handler.JsonbStringTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * Product style (SPU). One style matches one product-detail document in the knowledge base
 * <p>
 * Maps to t_product
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_product", autoResultMap = true)
public class ProductDO {

    /**
     * Primary key
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Style code, named from the same source as the knowledge-base product-detail document
     */
    private String spuCode;

    /**
     * Style name
     */
    private String name;

    /**
     * Top-level category
     */
    private String category;

    /**
     * Second-level category
     */
    private String subCategory;

    /**
     * Brand
     */
    private String brand;

    /**
     * Brand ownership. Only authorized brand and third-party brand
     */
    private String brandOwner;

    /**
     * On sale or delisted. Only a whole-style delisting is stored here
     */
    private String status;

    /**
     * Style-level shared attributes, for example {"屏幕":"6.3 英寸"}. Specs that do not vary with capacity or color belong here
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String specs;

    /**
     * Tags, comma-separated
     */
    private String tags;

    /**
     * One-line selling point. Accessories omit it
     */
    private String sellingPoint;

    /**
     * Created at
     */
    @TableField(fill = FieldFill.INSERT)
    private Timestamp createTime;

    /**
     * Updated at
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Timestamp updateTime;
}
