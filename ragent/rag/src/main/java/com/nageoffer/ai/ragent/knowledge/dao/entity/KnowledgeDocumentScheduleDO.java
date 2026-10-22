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

package com.nageoffer.ai.ragent.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Scheduled refresh task for a knowledge-base document
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_knowledge_document_schedule")
public class KnowledgeDocumentScheduleDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Document id
     */
    private String docId;

    /**
     * Knowledge-base id
     */
    private String kbId;

    /**
     * Schedule expression
     */
    private String cronExpr;

    /**
     * Whether the schedule is enabled
     */
    private Integer enabled;

    /**
     * Next run time
     */
    private Date nextRunTime;

    /**
     * Last run time
     */
    private Date lastRunTime;

    /**
     * Last success time
     */
    private Date lastSuccessTime;

    /**
     * Last run status
     */
    private String lastStatus;

    /**
     * Last error message
     */
    private String lastError;

    /**
     * Last ETag
     */
    private String lastEtag;

    /**
     * Last Last-Modified
     */
    private String lastModified;

    /**
     * Last content hash
     */
    private String lastContentHash;

    /**
     * Lock owner
     */
    private String lockOwner;

    /**
     * Lock expiry time
     */
    private Date lockUntil;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
