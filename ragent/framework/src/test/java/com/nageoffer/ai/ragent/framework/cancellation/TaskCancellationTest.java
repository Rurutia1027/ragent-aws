package com.nageoffer.ai.ragent.framework.cancellation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

class TaskCancellationTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void shouldReadInterruptFlagAsCancellation() {
        assertThat(TaskCancellation.isCancelled()).isFalse();

        Thread.currentThread().interrupt();

        assertThat(TaskCancellation.isCancelled()).isTrue();
        assertThat(TaskCancellation.isCancellation(new IOException("io"))).isTrue();
    }

    @Test
    void shouldRecognizeCancellationBuriedInCauseChain() {
        assertThat(TaskCancellation.isCancellation(new IOException("io"))).isFalse();
        assertThat(TaskCancellation.isCancellation(
                new CompletionException(new CancellationException("cancelled")))).isTrue();
        assertThat(TaskCancellation.isCancellation(
                new IllegalStateException(new InterruptedException("interrupted")))).isTrue();
    }

    @Test
    void shouldRestoreInterruptFlagWhenRebuildingCancellation() {
        CompletionException wrapped = new CompletionException(new InterruptedException("interrupted"));

        CancellationException cancellation = TaskCancellation.asCancellation(wrapped);

        assertThat(cancellation).hasCause(wrapped);
        // Throwing InterruptedException clears the interrupt flag. If it is not restored, upstream cancellation checks all fail.
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void shouldReuseExistingCancellationException() {
        CancellationException origin = new CancellationException("cancelled");

        assertThat(TaskCancellation.asCancellation(origin)).isSameAs(origin);
    }
}
