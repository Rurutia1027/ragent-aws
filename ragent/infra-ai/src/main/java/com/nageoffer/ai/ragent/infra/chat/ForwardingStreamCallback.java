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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pass-through StreamCallback decorator
 * <p>
 * onContent / onThinking pass straight to the delegate. onComplete / onError pass through, then CAS-once
 * fires {@link #onFinish(boolean, Throwable)} so trace, metering, and similar cases can finish when the stream ends
 */
public abstract class ForwardingStreamCallback implements StreamCallback {

    private final StreamCallback delegate;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicBoolean firstContentSeen = new AtomicBoolean(false);

    protected ForwardingStreamCallback(StreamCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public final void onContent(String content) {
        if (firstContentSeen.compareAndSet(false, true)) {
            try {
                onFirstContent();
            } catch (Throwable ex) {
                // A hook exception must not affect normal streaming
            }
        }
        delegate.onContent(content);
    }

    @Override
    public final void onThinking(String content) {
        delegate.onThinking(content);
    }

    @Override
    public final void onReplyToMessageId(String messageId) {
        delegate.onReplyToMessageId(messageId);
    }

    @Override
    public final void onSources(List<SourceRef> sources) {
        delegate.onSources(sources);
    }

    @Override
    public final void onGroundingChunks(List<GroundingChunk> chunks) {
        delegate.onGroundingChunks(chunks);
    }

    /**
     * Fires once when the stream reaches the first onContent. Often used to record the user-perceived first-token TTFT
     * Empty by default
     */
    protected void onFirstContent() {
    }

    @Override
    public final void onComplete() {
        try {
            delegate.onComplete();
        } finally {
            finishOnce(true, null);
        }
    }

    @Override
    public final void onError(Throwable error) {
        try {
            delegate.onError(error);
        } finally {
            finishOnce(false, error);
        }
    }

    /**
     * An external path (such as cancel) triggers cleanup and does not forward to the delegate
     */
    protected final void finishExternally(boolean success, Throwable error) {
        finishOnce(success, error);
    }

    private void finishOnce(boolean success, Throwable error) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        onFinish(success, error);
    }

    /**
     * Cleanup when the stream reaches a terminal state. Fires only once
     *
     * @param success whether it ended successfully
     * @param error   the failure exception, or null on success
     */
    protected abstract void onFinish(boolean success, Throwable error);
}
