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

/**
 * Execution-architecture configuration
 * Not nested under rag: in the AGENT tier the RAG pipeline is only one Tool of the main Agent, so the tier contains RAG rather than belonging to it
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ragent.engine")
public class OrchestrationProperties {

    /**
     * Tier value. workflow (default) or agent, case-insensitive
     */
    private String type = "workflow";

    public OrchestrationMode getMode() {
        return OrchestrationMode.of(type);
    }
}
