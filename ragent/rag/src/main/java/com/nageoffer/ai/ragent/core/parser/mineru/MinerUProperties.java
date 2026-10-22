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

package com.nageoffer.ai.ragent.core.parser.mineru;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MinerU SaaS configuration
 * <p>
 * Bound to the {@code mineru.*} section of application.yaml
 */
@Configuration
@ConfigurationProperties("mineru")
@Data
public class MinerUProperties {

    /**
     * MinerU SaaS API base URL. Default <a href="https://mineru.net/api/v4">...</a>
     */
    private String apiUrl = "https://mineru.net/api/v4";

    /**
     * API token, injected from the environment variable MINERU_API_KEY
     */
    private String apiKey;

    /**
     * Internal poll interval in seconds. Default 5
     */
    private int pollIntervalSeconds = 5;

    /**
     * Per-task timeout in seconds. Default 300
     */
    private int timeoutSeconds = 300;

    /**
     * Whether to extract tables. Default true
     */
    private boolean enableTable = true;

    /**
     * Whether to extract formulas. Default true
     */
    private boolean enableFormula = true;

    /**
     * Whether to force OCR. Default false (a native-text PDF does not need it)
     * <p>
     * The field name has no {@code is} prefix: Lombok adds an {@code is} prefix for a boolean getter {@link #isOcr()},
     * and the setter is {@link #setOcr(boolean)}. Spring {@code @ConfigurationProperties} reads the property name from
     * the setter, so it is {@code ocr}. The yaml must be {@code mineru.ocr: false}, not {@code is-ocr}
     */
    private boolean ocr = false;

    /**
     * Language code, following MinerU (PaddleOCR). Default ch (Chinese and English)
     */
    private String language = "ch";

    /**
     * Global cap on outstanding tasks, so the SaaS is not overwhelmed. Default 16
     */
    private int concurrencyLimit = 16;

    /**
     * Name of the distributed semaphore for MinerU parsing
     */
    private String semaphoreName = "rag:mineru:parse";

    /**
     * Maximum wait, in seconds, to acquire a MinerU parse permit
     */
    private int maxWaitSeconds = 30;

    /**
     * Auto-release time of a MinerU parse permit, in seconds. Must be greater than timeoutSeconds
     */
    private int leaseSeconds = 900;
}
