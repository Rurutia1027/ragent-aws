package com.nageoffer.ai.ragent.framework.cancellation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.CancellationException;

/**
 * Decides whether a failure is a user cancellation, so those exits are not recorded as faults.
 * The signal is the interrupt flag and the exception chain. After the user stops, the run handle disposes upstream and Reactor interrupts the worker.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TaskCancellation {

    /**
     * When a downstream call returns a fallback instead of throwing, the interrupt flag is the only signal left.
     */
    public static boolean isCancelled() {
        return Thread.currentThread().isInterrupted();
    }

    public static boolean isCancellation(Throwable error) {
        if (isCancelled()) {
            return true;
        }
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException || current instanceof CancellationException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Turns the failure into a cancellation and restores the interrupt flag that InterruptedException cleared.
     */
    public static CancellationException asCancellation(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (cause instanceof CancellationException cancellation) {
            return cancellation;
        }
        CancellationException cancellation = new CancellationException("Task cancelled");
        if (cause != null) {
            cancellation.initCause(cause);
        }
        return cancellation;
    }
}
