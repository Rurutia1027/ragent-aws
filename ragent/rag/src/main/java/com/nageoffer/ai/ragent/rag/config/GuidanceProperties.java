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

package com.nageoffer.ai.ragent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.guidance")
public class GuidanceProperties {

    /**
     * Whether guided Q&A is enabled
     */
    private Boolean enabled = true;

    /**
     * Ambiguity threshold
     * Ambiguity is triggered by duplicate candidate intent paths. This value is not used in the decision; it remains for existing yaml
     */
    private Double ambiguityScoreRatio = 0.8D;

    /**
     * Ambiguity-threshold buffer width
     * Same as {@link #ambiguityScoreRatio}; currently not used in the decision
     */
    private Double ambiguityMargin = 0.15D;

    /**
     * Maximum number of options shown at once
     */
    private Integer maxOptions = 6;
}
