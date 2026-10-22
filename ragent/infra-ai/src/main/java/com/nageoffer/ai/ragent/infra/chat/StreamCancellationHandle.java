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

package com.nageoffer.ai.ragent.infra.chat;

/**
 * Handle for cancelling a stream
 * <p>
 * Purpose:
 * - Controls streaming output from an LLM or a RAG system
 * - The caller can cancel streaming inference at any time (for example the user clicks "stop generating")
 * - Usually created and returned by the underlying streaming engine (LLM client, SSE, WebSocket)
 * <p>
 * Use cases:
 * - The user clicks "stop answering" during a streaming answer
 * - The backend times out, or the business needs to cancel inference early
 * - Used with StreamCallback so onContent() stops after the interrupt
 * <p>
 * Notes:
 * - cancel() must be idempotent: calling it more than once must not throw
 * - After cancel, the underlying model inference must be cancelled and resources released (threads, connections, and so on)
 */
public interface StreamCancellationHandle {

    /**
     * Cancel the current streaming inference task
     * <p>
     * Notes:
     * - The call should immediately try to cancel generation in the underlying model
     * - Commonly used when the user cancels (for example "Stop generating" in ChatGPT)
     * - After the call, onContent() callbacks should not continue
     */
    void cancel();
}
