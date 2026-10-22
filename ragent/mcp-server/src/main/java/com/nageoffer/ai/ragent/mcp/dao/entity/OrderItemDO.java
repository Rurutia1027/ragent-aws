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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Order line
 * <p>
 * Maps to t_order_item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order_item")
public class OrderItemDO {

    /**
     * Primary key
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Order number
     */
    private String orderNo;

    /**
     * Order code
     */
    private String skuCode;

    /**
     * Product-name snapshot at order time. Renaming the product does not change historical orders
     */
    private String skuName;

    /**
     * Unit-price snapshot at order time
     */
    private BigDecimal price;

    /**
     * Quantity
     */
    private Integer quantity;

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
