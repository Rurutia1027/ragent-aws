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
 * Order header
 * <p>
 * Maps to t_order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class OrderDO {

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
     * Platform user id. The business database has no cross-database foreign key
     */
    private String userId;

    /**
     * Order status
     */
    private String status;

    /**
     * Merchandise total
     */
    private BigDecimal totalAmount;

    /**
     * Discount amount
     */
    private BigDecimal discountAmount;

    /**
     * Amount paid
     */
    private BigDecimal payAmount;

    /**
     * Coupon code used
     */
    private String couponCode;

    /**
     * Receiver
     */
    private String receiverName;

    /**
     * Receiver phone. Masked before a query tool returns it
     */
    private String receiverPhone;

    /**
     * Shipping address. Query tools return only down to district
     */
    private String receiverAddress;

    /**
     * Tracking number
     */
    private String trackingNo;

    /**
     * Ordered at
     */
    @TableField(fill = FieldFill.INSERT)
    private Timestamp createTime;

    /**
     * Updated at
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Timestamp updateTime;

    /**
     * Paid at
     */
    private Timestamp payTime;

    /**
     * Shipped at
     */
    private Timestamp shipTime;

    /**
     * Received at. The seven-day no-reason window is computed from this
     */
    private Timestamp receiveTime;

    /**
     * Cancelled at
     */
    private Timestamp cancelTime;
}
