package com.nageoffer.ai.ragent.framework.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper around an SSE emitter.
 *
 * <p>Wraps Spring's SseEmitter with thread-safe sends and
 * one place that tracks a closed connection. Used to push a live stream to the client.</p>
 */
@Slf4j
public class SseEmitterSender {

    /**
     * Underlying Spring SSE emitter.
     */
    private final SseEmitter emitter;

    /**
     * Whether the connection is closed. An atomic flag keeps this thread-safe.
     * true once the connection is closed, false while it is still active.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Spring SseEmitter used for the actual SSE writes.
     */
    public SseEmitterSender(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));
    }

    /**
     * Sends one SSE event to the client.
     *
     * <p>Two send modes:</p>
     * <ul>
     *   <li>a null event name sends the default event</li>
     *   <li>a non-null event name sends a named event</li>
     * </ul>
     *
     * @param eventName event name, or null for the default event
     * @param data      payload to send
     */
    public void sendEvent(String eventName, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            if (eventName == null) {
                emitter.send(data);
                return;
            }
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Completes and closes the SSE connection.
     *
     * <p>CAS so the connection is closed only once.
     * Idempotent: only the first call takes effect.</p>
     */
    public void complete() {
        // CAS so close runs only once.
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    /**
     * Closes the SSE connection because of an error.
     *
     * <p>On failure this method:</p>
     * <ol>
     *   <li>closes the connection and reports the error to the client</li>
     *   <li>does not rethrow, so the global handler does not fight a response that has already started</li>
     * </ol>
     *
     * @param throwable the failure
     */
    public void fail(Throwable throwable) {
        closeWithError(throwable);
        log.warn("SSE send failed", throwable);
    }

    /**
     * Closes the connection with an error.
     * <p>
     * CAS so the connection is closed only once.
     * Calls completeWithError so the client sees an abnormal close.
     *
     * @param throwable the error that closed the connection
     */
    private void closeWithError(Throwable throwable) {
        // CAS so close runs only once.
        if (closed.compareAndSet(false, true)) {
            emitter.completeWithError(throwable);
        }
    }
}
