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

package com.nageoffer.ai.ragent.mcp.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nageoffer.ai.ragent.mcp.dao.entity.CartDO;
import com.nageoffer.ai.ragent.mcp.dao.result.CartLineResult;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/**
 * Shopping cart
 * Adds accumulate by delta. A decrease keeps at least one
 */
public interface CartMapper extends BaseMapper<CartDO> {

    String LINE_SQL = """
            SELECT c.id, c.user_id, c.sku_code, c.quantity, c.added_price, c.create_time, c.update_time,
                   s.name AS sku_name, s.price, s.stock, s.status, p.category
            FROM t_cart c
                     JOIN t_product_sku s ON s.sku_code = c.sku_code
                     JOIN t_product p ON p.spu_code = s.spu_code
            WHERE c.user_id = #{userId}
            """;

    @Select(LINE_SQL + " ORDER BY c.create_time DESC")
    List<CartLineResult> selectLines(@Param("userId") String userId);

    /**
     * Used when placing an order: lock the cart row, then read it. A later concurrent order from the same user waits here
     * <p>
     * FOR UPDATE OF c locks only the cart, not the product. Locking the product would serialize concurrent orders into one queue
     */
    @Select("""
            <script>
            """ + LINE_SQL + """
            <if test="skuCodes != null and skuCodes.size() > 0">
              AND c.sku_code IN <foreach item="code" collection="skuCodes" open="(" separator="," close=")">#{code}</foreach>
            </if>
            ORDER BY c.create_time
            FOR UPDATE OF c
            </script>
            """)
    List<CartLineResult> selectLinesForUpdate(@Param("userId") String userId,
                                              @Param("skuCodes") List<String> skuCodes);

    /**
     * The order transaction already locked this row and deducts only this purchase quantity. The executor deletes the row once the quantity hits zero.
     */
    @Update("""
            UPDATE t_cart SET quantity = quantity - #{quantity}, update_time = now()
            WHERE user_id = #{userId} AND sku_code = #{skuCode} AND quantity >= #{quantity}
            """)
    int deductQuantity(@Param("userId") String userId, @Param("skuCode") String skuCode,
                       @Param("quantity") int quantity);

    /**
     * Atomic increment inside the database, so a read-then-write does not lose concurrent adds. At the cap it does not update and returns null
     * RETURNING gives the quantity after this write. A conflict update keeps the original add-to-cart price and time
     */
    @Select(value = """
            INSERT INTO t_cart (id, user_id, sku_code, quantity, added_price, create_time, update_time)
            VALUES (#{id}, #{userId}, #{skuCode}, #{quantity}, #{addedPrice}, #{now}, #{now})
            ON CONFLICT (user_id, sku_code)
            DO UPDATE SET quantity = t_cart.quantity + EXCLUDED.quantity, update_time = #{now}
            WHERE t_cart.quantity + EXCLUDED.quantity <= #{maxQuantity}
            RETURNING quantity
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Integer addQuantity(@Param("id") long id, @Param("userId") String userId, @Param("skuCode") String skuCode,
                        @Param("quantity") int quantity, @Param("addedPrice") BigDecimal addedPrice,
                        @Param("maxQuantity") int maxQuantity, @Param("now") Timestamp now);

    default Integer addQuantity(String userId, String skuCode, int quantity, BigDecimal addedPrice, int maxQuantity) {
        return addQuantity(IdWorker.getId(), userId, skuCode, quantity, addedPrice, maxQuantity,
                new Timestamp(System.currentTimeMillis()));
    }

    /**
     * Decrease the cart quantity, keeping at least one. Returns null when the row does not exist
     */
    @Select(value = """
            UPDATE t_cart SET quantity = GREATEST(quantity - #{quantity}, 1), update_time = now()
            WHERE user_id = #{userId} AND sku_code = #{skuCode}
            RETURNING quantity
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Integer decreaseQuantity(@Param("userId") String userId, @Param("skuCode") String skuCode,
                             @Param("quantity") int quantity);
}
