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

package com.nageoffer.ai.ragent.ingestion.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Ingestion task status.
 * Execution status of a document ingestion task.
 * Values use lower snake_case, such as pending, running, completed
 */
@Getter
@RequiredArgsConstructor
public enum IngestionStatus {

    /**
     * Pending: the task is created and has not started
     */
    PENDING("pending"),

    /**
     * Running: the task is executing
     */
    RUNNING("running"),

    /**
     * Failed: the task failed
     */
    FAILED("failed"),

    /**
     * Completed: the task finished successfully
     */
    COMPLETED("completed");

    /**
     * Status value (lower snake_case)
     */
    private final String value;

    /**
     * Resolves the status from a string value
     */
    @JsonCreator
    public static IngestionStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        for (IngestionStatus status : values()) {
            if (status.value.equalsIgnoreCase(normalized) || status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ingestion status: " + value);
    }

    private static String normalize(String value) {
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase();
        return lower.replace('-', '_');
    }

    /**
     * Returns the serialized value
     */
    @JsonValue
    public String getValue() {
        return value;
    }
}
