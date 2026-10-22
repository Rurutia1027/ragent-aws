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

package com.nageoffer.ai.ragent.rag.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Agent view
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentProfileVO {

    private String id;
    private String name;
    private String description;

    /**
     * Avatar preset id
     */
    private String avatar;

    /**
     * A built-in agent cannot be edited or deleted
     */
    private Boolean builtin;

    private Boolean active;

    /**
     * Count of slots this agent has filled that the current architecture actually reads. The rest fall back to the built-in agent
     */
    private Integer effectiveSlots;

    /**
     * Count of filled slots the current architecture does not read. They apply only after ragent.engine.type changes
     */
    private Integer inactiveSlots;

    private Date createTime;
    private Date updateTime;
}
