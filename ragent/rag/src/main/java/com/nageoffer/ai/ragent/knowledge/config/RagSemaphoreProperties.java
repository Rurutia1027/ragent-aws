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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Distributed semaphore config
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "rag.semaphore")
public class RagSemaphoreProperties {

    @Valid
    private PermitExpirableConfig documentUpload = new PermitExpirableConfig();

    @Data
    public static class PermitExpirableConfig {

        /**
         * Redisson semaphore name
         */
        @NotBlank
        private String name = "rag:document:upload";

        /**
         * Max concurrency
         */
        @Min(1)
        private Integer maxConcurrent = 10;

        /**
         * Max wait to acquire a permit (seconds)
         */
        @Min(0)
        private Integer maxWaitSeconds = 30;

        /**
         * Permit auto-release time (seconds)
         */
        @Min(1)
        private Integer leaseSeconds = 30;
    }
}
