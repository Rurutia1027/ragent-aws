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

/**
 * Model client error types
 * <p>
 * Error categories that can occur when calling an AI model service,
 * used for consistent classification and handling
 */
public enum ModelClientErrorType {

    /**
     * Unauthorized: authentication failed or the token is invalid
     */
    UNAUTHORIZED,

    /**
     * Rate limit: request rate exceeded the allowance
     */
    RATE_LIMITED,

    /**
     * Server error: internal error on the model service
     */
    SERVER_ERROR,

    /**
     * Client error: invalid request parameters or format
     */
    CLIENT_ERROR,

    /**
     * Network error: connection or timeout failure
     */
    NETWORK_ERROR,

    /**
     * Invalid response: the model response format is incorrect
     */
    INVALID_RESPONSE,

    /**
     * Provider error: the model provider service failed
     */
    PROVIDER_ERROR;

    /**
     * Infer the error type from an HTTP status code
     *
     * @param status HTTP status code
     * @return matching error type
     */
    public static ModelClientErrorType fromHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return UNAUTHORIZED;
        }
        if (status == 429) {
            return RATE_LIMITED;
        }
        if (status >= 500) {
            return SERVER_ERROR;
        }
        return CLIENT_ERROR;
    }
}
