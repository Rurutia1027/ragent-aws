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

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nageoffer.ai.ragent.knowledge.dao.handler.StringListTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_intent_node", autoResultMap = true)
public class IntentNodeDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Knowledge base ID
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String kbId;

    /**
     * Business unique id, such as group-hr / biz-oa-intro
     */
    private String intentCode;

    /**
     * Display name
     */
    private String name;

    /**
     * Level: 0=DOMAIN, 1=CATEGORY, 2=TOPIC
     */
    private Integer level;

    /**
     * Parent node's intent_code
     */
    private String parentCode;

    /**
     * Description
     */
    private String description;

    /**
     * Example questions: a JSON array string
     */
    private String examples;

    /**
     * Milvus collection name (meaningful only when kind=0)
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String collectionName;

    /**
     * Associated collection names (JSONB)
     * collectionName remains only for old data. New logic uses this field
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> collectionNames;

    /**
     * MCP tool ID (meaningful only when kind=2)
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mcpToolId;

    /**
     * Whether the user must confirm before execution: 0=no, 1=yes (meaningful only when kind=2)
     * The integrator checks this for write-style MCP tools. The Agent shows a confirmation card before calling
     */
    private Integer requireConfirm;

    /**
     * Per-node retrieval TopK (optional)
     * When empty, the global default TopK is used
     */
    private Integer topK;

    /**
     * Type: 0=KB(RAG), 1=SYSTEM, 2=MCP
     */
    private Integer kind;

    /**
     * Sort order
     */
    private Integer sortOrder;

    /**
     * Short rule snippet (optional)
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String promptSnippet;

    /**
     * Full prompt template for the scene (optional)
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String promptTemplate;

    /**
     * Whether enabled
     */
    private Integer enabled;

    private String createBy;
    private String updateBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
