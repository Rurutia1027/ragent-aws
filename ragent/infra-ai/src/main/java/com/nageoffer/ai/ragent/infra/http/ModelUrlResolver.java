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

package com.nageoffer.ai.ragent.infra.http;

import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Model URL resolver
 * Resolves the full URL of an AI model, from the candidate model settings or the provider settings
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class ModelUrlResolver {

    /**
     * Resolve a model URL
     * Priority: candidate model URL, then provider base URL plus the endpoint path
     *
     * @param provider   provider settings, including the base URL and endpoints
     * @param candidate  candidate model settings, which may include a custom URL
     * @param capability model capability, used to pick the endpoint
     * @return the full model URL
     * @throws IllegalStateException when the provider base URL or the endpoint settings are missing
     */
    public static String resolveUrl(
            AIModelProperties.ProviderConfig provider,
            AIModelProperties.ModelCandidate candidate,
            ModelCapability capability) {
        if (candidate != null && candidate.getUrl() != null && !candidate.getUrl().isBlank()) {
            return candidate.getUrl();
        }
        if (provider == null || provider.getUrl() == null || provider.getUrl().isBlank()) {
            throw new IllegalStateException("Provider baseUrl is missing");
        }

        Map<String, String> endpoints = provider.getEndpoints();
        String key = capability.name().toLowerCase();
        String path = endpoints == null ? null : endpoints.get(key);
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Provider endpoint is missing: " + key);
        }

        return joinUrl(provider.getUrl(), path);
    }

    /**
     * Join a base URL and a path
     * Handles the slash between the URL and the path so the result is well formed
     *
     * @param baseUrl base URL
     * @param path    path
     * @return the joined URL
     */
    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl + path.substring(1);
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }
}
