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

package com.nageoffer.ai.ragent.rag.controller.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentNodeCreateRequest {

    private String kbId;
    private List<String> collectionNames;
    private String intentCode;
    private String name;
    /**
     * 0=DOMAIN,1=CATEGORY,2=TOPIC
     */
    private Integer level;
    private String parentCode;
    private String description;
    private List<String> examples;
    private String mcpToolId;

    /**
     * Whether the user must confirm before execution: 0=no, 1=yes (meaningful only when kind=2)
     */
    private Integer requireConfirm;
    private Integer topK;
    private Integer kind;
    private Integer sortOrder;
    private Integer enabled;

    /**
     * Short rule snippet (optional)
     */
    private String promptSnippet;

    /**
     * Full prompt template for the scene (optional)
     */
    private String promptTemplate;

}
