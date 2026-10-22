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

/**
 * Agent skill entity
 * A skill is an operating manual for the model. The body is plain Markdown
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_agent_skill", autoResultMap = true)
public class AgentSkillDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Skill id, the name the model reports when loading the body, such as leave_apply
     */
    private String skillCode;

    /**
     * Skill display name
     */
    private String name;

    /**
     * When to use it. Sent with the skill list so the model can decide whether to load the body
     */
    private String description;

    /**
     * Skill body Markdown
     */
    private String content;

    /**
     * MCP tool IDs unlocked only after this skill is loaded. Values must come from MCP nodes on the intent tree
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> toolIds;

    /**
     * Sort order. Smaller values come first
     */
    private Integer sortOrder;

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
