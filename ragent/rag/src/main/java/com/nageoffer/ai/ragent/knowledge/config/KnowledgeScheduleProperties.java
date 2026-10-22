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

package com.nageoffer.ai.ragent.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Knowledge-base schedule config
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "rag.knowledge.schedule")
public class KnowledgeScheduleProperties {

    /**
     * Scan interval (milliseconds)
     */
    private Long scanDelayMs = 10000L;

    /**
     * How long the distributed lock is held (seconds)
     */
    private Long lockSeconds = 900L;

    /**
     * Batch size per scan
     */
    private Integer batchSize = 20;

    /**
     * Minimum interval for a scheduled fetch (seconds)
     */
    private Long minIntervalSeconds = 60L;

    /**
     * Timeout for RUNNING (minutes). A document unfinished past this is reset to FAILED
     */
    private Long runningTimeoutMinutes = 30L;
}
