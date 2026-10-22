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

import com.nageoffer.ai.ragent.framework.convention.GroundingChunk;
import com.nageoffer.ai.ragent.framework.convention.SourceRef;

import java.util.List;

/**
 * Streaming response callback (StreamCallback)
 * <p>
 * Purpose:
 * - Handles streaming output from an LLM or a RAG system
 * - While the model is generating, onContent() is called repeatedly with increments
 * - onComplete() fires when the answer ends, and onError() fires on failure
 * <p>
 * Use cases:
 * - SSE / WebSocket streaming responses
 * - A backend streaming from a local model (Ollama, DeepSeek, Qwen)
 * - The frontend rendering a message live and concatenating the answer as it arrives
 * <p>
 * Notes:
 * - onContent() is called an unpredictable number of times, set by the model's output pace
 * - onComplete() must be called exactly once on a normal end
 * - onError() should catch every failure so the client experience is not broken
 */
public interface StreamCallback {

    /**
     * Record the user message id for the current answer
     *
     * @param messageId user message id
     */
    default void onReplyToMessageId(String messageId) {
    }

    /**
     * Receive one increment (a delta token or a fragment)
     * <p>
     * Notes:
     * - Every chunk the model pushes is delivered through this callback
     * - The chunk may be a character, a word, or a sentence, depending on the model's split strategy
     * <p>
     * Example:
     * onContent("Hello");
     * onContent(", I can help with that");
     *
     * @param content the increment just pushed (not the full answer)
     */
    void onContent(String content);

    /**
     * Receive an increment of the thinking trace (if the model supports it)
     * <p>
     * Empty by default. Callers that do not support thinking can ignore it
     *
     * @param content the thinking content just pushed
     */
    default void onThinking(String content) {
    }

    /**
     * Receive answer sources (document level)
     * <p>
     * Called once after retrieval. The implementation stores them and sends them with the completion event (onComplete). Empty by default
     *
     * @param sources document-level source list
     */
    default void onSources(List<SourceRef> sources) {
    }

    /**
     * Receive grounding chunks for recommended questions
     * <p>
     * Called once after retrieval. The implementation stores them and persists them with the assistant message, for later grounding of follow-up recommendations. Empty by default
     *
     * @param chunks grounding chunk list
     */
    default void onGroundingChunks(List<GroundingChunk> chunks) {
    }

    /**
     * The whole inference flow has finished (every chunk has been pushed)
     * <p>
     * Purpose:
     * - Tell the upper layer to finish the UI: stop loading, scroll to the bottom, assemble the full answer, and so on
     * - Must fire after every onContent() call has completed
     */
    void onComplete();

    /**
     * A failure during streaming
     * <p>
     * Typical cases:
     * - An error inside the model
     * - A network break
     * - A timeout
     * - A parse error
     *
     * @param error the exception, with the concrete error
     */
    void onError(Throwable error);
}
