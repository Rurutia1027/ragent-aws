package com.nageoffer.ai.ragent.framework.cacellation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.bytebuddy.implementation.bytecode.Throw;

import java.util.concurrent.CancellationException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskCancellation {
    public static boolean isCancelled() {
        return Thread.currentThread().isInterrupted();
    }

    public static boolean isCancellation(Throwable error) {
        if (isCancelled()) {
            return true;
        }

        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    public static CancellationException asCancellation(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof  InterruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (cause instanceof CancellationException cancellation) {
            return cancellation;
        }

        CancellationException cancellation = new CancellationException("Task Cancelled");
        if (cause != null) {
            cancellation.initCause(cause);
        }
        return cancellation;

    }
}
