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

import lombok.Data;

/**
 * Query-term mapping update request
 */
@Data
public class QueryTermMappingUpdateRequest {

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
     * Priority. Smaller values rank higher
     */
    private Integer priority;

    /**
     * Whether active
     */
    private Boolean enabled;

    /**
     * Remark
     */
    private String remark;
}
