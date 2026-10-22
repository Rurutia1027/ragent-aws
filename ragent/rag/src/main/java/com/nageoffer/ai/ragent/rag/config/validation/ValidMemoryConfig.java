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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Memory configuration validation annotation
 * Checks that summary settings are consistent
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MemoryConfigValidator.class)
@Documented
public @interface ValidMemoryConfig {

    /**
     * Default message when validation fails
     * <p>
     * Required by JSR-303 even when the Validator supplies its own message
     * </p>
     */
    String message() default "记忆配置不合法";

    /**
     * Validation groups
     * <p>
     * Required by JSR-303 to support grouped validation (for example, validate on create and on update)
     * Must be kept even when grouped validation is unused
     * </p>
     */
    Class<?>[] groups() default {};

    /**
     * Payload
     * <p>
     * Required by JSR-303 to carry validation metadata (for example, severity or an error code)
     * Must be kept even when Payload is unused
     * </p>
     */
    Class<? extends Payload>[] payload() default {};
}
