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

import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.enums.Tier;

/**
 * Common large-language-model (LLM) access interface
 * <p>
 * Purpose:
 * - Gives the business layer one way to call a model, hiding vendor and protocol differences
 * - Supports a sync call (one complete answer) and a streaming call (incremental tokens or fragments)
 * - Implementations adapt each platform, for example:
 * - Local inference (Ollama, LM Studio, and others)
 * - Alibaba Cloud Model Studio (DashScope)
 * - DeepSeek / OpenAI / Qwen API
 * - An in-house inference service
 * <p>
 * Core capabilities:
 * - Standard prompt construction (system / user / context)
 * - RAG support (retrieved context can be passed in)
 * - Parameter control (temperature, top_p, max_tokens, stop, and others)
 * - Streaming token output (with StreamCallback)
 * <p>
 * Notes:
 * - The default tier is standard. A call site that wants a faster or stronger model passes a Tier override. Deep thinking (thinking=true) uses deep-thinking-tier
 * - Complex cases (context, multi-turn dialogue, generation parameters) are expressed with ChatRequest
 * - Streaming must handle cancel() correctly and release resources
 */
public interface LLMService {

    /**
     * Sync call (default tier)
     * <p>
     * Notes:
     * - The tier defaults to standard when no Tier override is passed. Deep thinking is expressed by request.thinking and, when set, uses deep-thinking-tier
     * - Supports fine control of the system prompt, message list, and generation parameters
     *
     * @param request ChatRequest with the full request configuration
     * @return the complete answer returned by the model
     */
    String chat(ChatRequest request);

    /**
     * Sync call (explicit tier override)
     * <p>
     * Notes:
     * - tier names the tier explicitly (passed by the call site when it wants a faster or stronger model) and overrides the default standard
     * - Deep thinking still wins: request.thinking=true uses deep-thinking-tier
     *
     * @param request ChatRequest with the full request configuration
     * @param tier    target tier
     * @return the complete answer returned by the model
     */
    String chat(ChatRequest request, Tier tier);

    /**
     * Sync call (tier override plus a preferred model)
     * <p>
     * Notes (preferred semantics):
     * - Prefer the model named by preferredModelId, then fall back to the other candidates of the tier
     * - An empty preferredModelId is the same as chat(request, tier)
     *
     * @param request          ChatRequest with the full request configuration
     * @param tier             fallback tier
     * @param preferredModelId preferred model id, or the tier candidates when empty
     * @return the complete answer returned by the model
     */
    String chat(ChatRequest request, Tier tier, String preferredModelId);

    /**
     * Streaming call (default tier)
     * <p>
     * Notes:
     * - The tier defaults to standard. Deep thinking is expressed by request.thinking and, when set, uses deep-thinking-tier
     * - Every increment is delivered through callback.onContent(), completion through onComplete(), and failure through onError()
     *
     * @param request  ChatRequest with the full request configuration
     * @param callback streaming callback
     * @return StreamCancellationHandle used to cancel inference
     */
    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback);
}
