package com.nageoffer.ai.ragent.framework.trace;

/**
 * Trace support for a stream node that crosses threads.
 * <p>
 * AOP on @RagTraceNode only sees the submit (runAsync) in a stream:
 * the synchronous part (HTTP submit and first-packet wait) runs on the caller, and the SSE read loop runs on a pool
 * worker. This lets the caller open a cross-thread node that the SSE
 * onComplete / onError callbacks finish on the other thread.
 */
public interface RagStreamTraceSupport {

    /**
     * Opens a stream node on the caller thread: inserts a RUNNING row and pushes nodeId onto the current
     * thread's NODE_STACK so a nested synchronous child, such as first-packet, can see its parent.
     * <p>
     * The returned {@link StreamSpan} must call
     * {@link StreamSpan#detach()} before the caller's synchronous section ends. finish runs on the async thread.
     */
    StreamSpan beginStreamNode(String name, String type);

    interface StreamSpan {

        /**
         * Called when the caller's synchronous section ends. Pops nodeId off NODE_STACK.
         * Does not finish the node.
         */
        void detach();

        /**
         * Called from the async thread on completion. CAS keeps it idempotent.
         */
        void finishSuccess();

        /**
         * Called from the async thread on error. CAS keeps it idempotent.
         */
        void finishError(Throwable error);

        /**
         * Cancel path. If the node is still running, finish it as cancelled so it does not stay RUNNING.
         */
        void finishCancelledIfRunning();
    }

    /**
     * No-op span used when tracing is disabled.
     */
    StreamSpan NOOP_SPAN = new StreamSpan() {
        @Override public void detach() {}
        @Override public void finishSuccess() {}
        @Override public void finishError(Throwable error) {}
        @Override public void finishCancelledIfRunning() {}
    };
}
