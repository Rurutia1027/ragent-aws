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

package com.nageoffer.ai.ragent.ingestion.domain.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Node execution result.
 * Result of one node in the pipeline: status, and whether later nodes should run
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeResult {

    /**
     * Whether the node succeeded
     */
    private boolean success;

    /**
     * Whether later nodes should run
     */
    private boolean shouldContinue;

    /**
     * Result message
     */
    private String message;

    /**
     * Exception when the node failed
     */
    private Throwable error;

    /**
     * Creates a success result
     *
     * @return a result that succeeded and should continue
     */
    public static NodeResult ok() {
        return NodeResult.builder().success(true).shouldContinue(true).build();
    }

    /**
     * Creates a success result with a message
     *
     * @param message result message
     * @return a result that succeeded and should continue
     */
    public static NodeResult ok(String message) {
        return NodeResult.builder().success(true).shouldContinue(true).message(message).build();
    }

    /**
     * Creates a skipped result
     *
     * @param reason why it was skipped
     * @return a result for a skipped node that should still continue
     */
    public static NodeResult skip(String reason) {
        return NodeResult.builder().success(true).shouldContinue(true).message("Skipped: " + reason).build();
    }

    /**
     * Creates a failure result
     *
     * @param error exception
     * @return a result that failed and should not continue
     */
    public static NodeResult fail(Throwable error) {
        return NodeResult.builder()
                .success(false)
                .shouldContinue(false)
                .error(error)
                .message(error == null ? null : error.getMessage())
                .build();
    }

    /**
     * Creates a stop result
     *
     * @param reason why it stopped
     * @return a result that succeeded but should stop the pipeline
     */
    public static NodeResult terminate(String reason) {
        return NodeResult.builder().success(true).shouldContinue(false).message(reason).build();
    }
}
