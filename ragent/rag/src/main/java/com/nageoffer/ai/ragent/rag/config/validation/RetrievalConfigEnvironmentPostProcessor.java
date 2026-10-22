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

package com.nageoffer.ai.ragent.rag.config.validation;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;

/**
 * Retrieval-channel configuration consistency check (the earliest core startup hook)
 * <p>
 * {@link EnvironmentPostProcessor} runs during {@code prepareEnvironment()}, before any bean, the web container, and the banner,
 * and reads the whole {@link ConfigurableEnvironment}. A channel enabled while its backend is not assembled throws {@link RetrievalConfigException}
 * and aborts startup. {@link RetrievalConfigFailureAnalyzer} renders the same style of diagnostic as a port-in-use failure
 * <p>
 * Registered via {@code META-INF/spring.factories}
 * <p>
 * Ordered at {@link Ordered#LOWEST_PRECEDENCE}: it must run after the ConfigDataEnvironmentPostProcessor that loads application.yaml,
 * or the properties are missing and the check is skipped
 */
public class RetrievalConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<RetrievalChannelConfigValidator.Violation> violations = RetrievalChannelConfigValidator.validate(
                environment::getProperty,
                key -> environment.getProperty(key, Boolean.class, false));
        if (!violations.isEmpty()) {
            throw new RetrievalConfigException(violations);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
