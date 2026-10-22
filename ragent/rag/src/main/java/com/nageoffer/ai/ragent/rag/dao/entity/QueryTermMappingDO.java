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

package com.nageoffer.ai.ragent.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_query_term_mapping")
public class QueryTermMappingDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Business domain or system id, such as biz, group, or data_security. Optional
     */
    private String domain;

    /**
     * User's original phrase
     */
    private String sourceTerm;

    /**
     * Normalized target phrase
     */
    private String targetTerm;

    /**
     * Match type. 1: exact, 2: prefix, 3: regex, 4: whole word
     */
    private Integer matchType;

    /**
     * Priority. Smaller values rank higher (longer phrases usually come first)
     */
    private Integer priority;

    /**
     * Whether active. 1: active, 0: disabled
     */
    private Integer enabled;

    /**
     * Remark
     */
    private String remark;

    private String createBy;

    private String updateBy;

    private Date createTime;

    private Date updateTime;
}
