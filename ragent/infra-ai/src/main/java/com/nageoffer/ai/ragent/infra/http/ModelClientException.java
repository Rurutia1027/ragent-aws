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

import lombok.Getter;

/**
 * Model client exception
 * Wraps errors raised while calling a model
 */
@Getter
public class ModelClientException extends RuntimeException {

    /**
     * Error type
     */
    private final ModelClientErrorType errorType;

    /**
     * HTTP status code
     */
    private final Integer statusCode;

    /**
     * Model client exception with a cause
     *
     * @param message    exception message
     * @param errorType  error type
     * @param statusCode HTTP status code
     * @param cause      original exception
     */
    public ModelClientException(String message, ModelClientErrorType errorType, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }

    /**
     * Model client exception
     *
     * @param message    exception message
     * @param errorType  error type
     * @param statusCode HTTP status code
     */
    public ModelClientException(String message, ModelClientErrorType errorType, Integer statusCode) {
        super(message);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }
}
