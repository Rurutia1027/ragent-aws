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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that forces the response encoding to UTF-8
 *
 * <p>
 * Why:
 * In some environments, leaving the response character encoding unset can produce garbled Chinese
 * or inconsistent default encodings across components. This filter forces UTF-8 before any response is written
 * </p>
 *
 * <p>
 * Usage:
 * With {@link Component}, Spring Boot registers it as a Servlet Filter automatically.
 * It applies to every request (unless a FilterRegistrationBean restricts path or order)
 * </p>
 */
@Component
public class Utf8ResponseFilter implements Filter {

    /**
     * Set the response encoding to UTF-8 and continue the filter chain
     *
     * @param request  current HTTP request
     * @param response current HTTP response
     * @param chain    filter chain, passing the request/response to the next filter or the target Servlet
     * @throws IOException      I/O failure
     * @throws ServletException Servlet-level failure
     */
    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        // Force the character encoding to UTF-8 before the response is written
        response.setCharacterEncoding("UTF-8");

        // Continue to the next filter or the final target
        chain.doFilter(request, response);
    }
}

