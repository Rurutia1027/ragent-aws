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
import com.nageoffer.ai.ragent.mcp.dao.entity.ProductDO;
import com.nageoffer.ai.ragent.mcp.dao.result.ProductSummaryResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product style (SPU)
 * <p>
 * Browsing is always by style. Capacities and colors of one style are one row, not sixteen. A specific configuration goes through ProductSkuMapper
 */
public interface ProductMapper extends BaseMapper<ProductDO> {

    /**
     * The price condition applies to a single configuration inside the subquery, so "tablets from 5000 to 8000" can hit a style whose starting price is 3799
     * but which has a 5999 configuration. The echoed range also stays inside what the user asked for
     * <p>
     * Sort layers "accessory or not" first: accessory names usually include the compatible model ("适用于 iPhone 17"),
     * and there are twenty times as many accessories as devices. Without the layer, "how is the iPhone 17" returns ten cases first.
     * Both kinds stay in the result, devices first. Filtering by category makes this layer a no-op
     */
    @Select("""
            <script>
            SELECT p.spu_code, p.name, p.category, p.sub_category, p.brand, p.brand_owner,
                   p.specs::text AS specs, p.tags, p.selling_point,
                   a.sku_count, a.stock, a.min_price, a.max_price
            FROM t_product p
                     JOIN (SELECT spu_code,
                                  COUNT(*)   AS sku_count,
                                  SUM(stock) AS stock,
                                  MIN(price) AS min_price,
                                  MAX(price) AS max_price
                           FROM t_product_sku
                           WHERE status = '在售'
                           <if test="minPrice != null"> AND price &gt;= #{minPrice}</if>
                           <if test="maxPrice != null"> AND price &lt;= #{maxPrice}</if>
                           GROUP BY spu_code) a ON a.spu_code = p.spu_code
            WHERE p.status = '在售'
            <if test="category != null"> AND p.category = #{category}</if>
            <if test="keyword != null">
              AND (p.name ILIKE '%' || #{keyword} || '%' OR p.sub_category ILIKE '%' || #{keyword} || '%'
                   OR p.brand ILIKE '%' || #{keyword} || '%' OR p.tags ILIKE '%' || #{keyword} || '%'
                   OR p.selling_point ILIKE '%' || #{keyword} || '%')
            </if>
            <foreach item="tag" collection="tags">
              AND p.tags ILIKE '%' || #{tag} || '%'
            </foreach>
            ORDER BY CASE WHEN p.category = '配件' THEN 1 ELSE 0 END,
                     a.min_price <if test="priceDesc">DESC</if><if test="!priceDesc">ASC</if>, p.spu_code
            LIMIT #{limit}
            </script>
            """)
    List<ProductSummaryResult> selectSummaries(@Param("category") String category,
                                               @Param("keyword") String keyword,
                                               @Param("tags") List<String> tags,
                                               @Param("minPrice") BigDecimal minPrice,
                                               @Param("maxPrice") BigDecimal maxPrice,
                                               @Param("priceDesc") boolean priceDesc,
                                               @Param("limit") int limit);
}
