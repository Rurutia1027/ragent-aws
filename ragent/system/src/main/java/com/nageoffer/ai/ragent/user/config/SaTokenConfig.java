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

package com.nageoffer.ai.ragent.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token configuration.
 * Registers the login interceptor and the user-context interceptor.
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * User-context interceptor.
     */
    private final UserContextInterceptor userContextInterceptor;

    /**
     * Global interceptor order: login (0), demo read-only (10, registered by RagentWebMvcConfiguration), user context (20).
     */
    public static final int ORDER_LOGIN = 0;
    public static final int ORDER_DEMO_MODE = 10;
    public static final int ORDER_USER_CONTEXT = 20;

    /**
     * Register interceptors.
     *
     * @param registry interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register the Sa-Token login interceptor
        registry.addInterceptor(new SaInterceptor(handler -> {
                    // Skip the login check for async dispatches (an SSE completion callback triggers asyncDispatch after the Sa-Token context is gone)
                    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attrs != null) {
                        HttpServletRequest request = attrs.getRequest();
                        // Skip the login check when this is an async dispatch
                        if (request.getDispatcherType() == DispatcherType.ASYNC) {
                            return;
                        }
                        // Allow preflight requests through so CORS is not blocked
                        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                            return;
                        }
                    }
                    // Check that the caller is logged in
                    StpUtil.checkLogin();
                }))
                // Intercept every path
                .addPathPatterns("/**")
                // Exclude auth paths and the error page
                .excludePathPatterns("/auth/**", "/error")
                .order(ORDER_LOGIN);

        // Register the user-context interceptor
        registry.addInterceptor(userContextInterceptor)
                // Intercept every path
                .addPathPatterns("/**")
                // Exclude auth paths and the error page
                .excludePathPatterns("/auth/**", "/error")
                .order(ORDER_USER_CONTEXT);
    }
}
