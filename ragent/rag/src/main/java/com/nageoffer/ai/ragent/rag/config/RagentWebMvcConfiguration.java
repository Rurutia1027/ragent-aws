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

import com.nageoffer.ai.ragent.user.config.SaTokenConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;

/**
 * Web MVC configuration
 *
 * <p>
 * Configures Spring MVC message converters so string responses use UTF-8 and avoid garbled Chinese or mixed encodings
 * </p>
 *
 * <p>
 * By default Spring Boot configures a set of HTTP message converters.
 * {@link StringHttpMessageConverter} may not use UTF-8; this configuration replaces the default string converter
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class RagentWebMvcConfiguration implements WebMvcConfigurer {

    /**
     * Demo read-only interceptor. It is a rag-domain concept, so it is registered here rather than in the login configuration
     */
    private final DemoModeInterceptor demoModeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Order sits between login and user context, matching the original SaTokenConfig registration site
        registry.addInterceptor(demoModeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/error")
                .order(SaTokenConfig.ORDER_DEMO_MODE);
    }

    /**
     * Custom message-converter configuration
     *
     * <p>
     * Replaces the default {@link StringHttpMessageConverter} via the Spring Framework 7 builder
     * and leaves JSON and the other default converters in place
     * </p>
     *
     * @param builder Spring MVC message-converter builder
     */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        // Use UTF-8 as the default encoding for string responses
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);

        // Do not append a charset list (accept-charset) to the response Content-Type header,
        // Some clients and middleware cannot parse that header
        stringConverter.setWriteAcceptCharset(false);

        // Replace the default String converter and keep JSON and the other default converters
        builder.withStringConverter(stringConverter);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
