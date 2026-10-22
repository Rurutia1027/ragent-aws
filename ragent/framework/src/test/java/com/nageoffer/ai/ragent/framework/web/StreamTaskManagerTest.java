package com.nageoffer.ai.ragent.framework.web;

import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamTaskManagerTest {

    private static final String TASK_ID = "t-1001";
    private static final String OWNER_ID = "u-1";
    private static final String OTHER_ID = "u-2";
    private static final String CANCEL_TOPIC = "ragent:stream:cancel";
    private static final String CANCEL_KEY = "ragent:stream:cancel:" + TASK_ID;
    private static final String OWNER_KEY = "ragent:stream:owner:" + TASK_ID;

    private RBucket<String> cancelBucket;
    private RBucket<String> ownerBucket;
    private RTopic topic;
    private StreamTaskManager taskManager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cancelBucket = mock(RBucket.class);
        ownerBucket = mock(RBucket.class);
        topic = mock(RTopic.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        when(redissonClient.<String>getBucket(CANCEL_KEY)).thenReturn(cancelBucket);
        when(redissonClient.<String>getBucket(OWNER_KEY)).thenReturn(ownerBucket);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        taskManager = new StreamTaskManager(redissonClient);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRecordOwnerOnRegister() {
        taskManager.register(TASK_ID, OWNER_ID, () -> {
        });

        // The owner must be visible across nodes: the stop request may land on a node that is not running the stream.
        verify(ownerBucket).set(OWNER_ID, Duration.ofMinutes(30));
    }

    @Test
    void shouldRejectCancelFromNonOwner() {
        when(ownerBucket.get()).thenReturn(OWNER_ID);
        login(OTHER_ID);

        assertThatThrownBy(() -> taskManager.cancelByUser(TASK_ID))
                .isInstanceOf(ClientException.class);

        // taskId is a time-ordered snowflake id, so an unauthorized caller must not leave any cancel trace.
        verify(cancelBucket, never()).set(anyString(), any(Duration.class));
        verify(topic, never()).publish(any());
    }

    @Test
    void shouldCancelWhenRequesterIsOwner() {
        when(ownerBucket.get()).thenReturn(OWNER_ID);
        login(OWNER_ID);

        taskManager.cancelByUser(TASK_ID);

        verify(cancelBucket).set(OWNER_ID, Duration.ofMinutes(30));
        // The payload carries the requester so the executor can check it again.
        verify(topic).publish(TASK_ID + "|" + OWNER_ID);
    }

    @Test
    void shouldIgnoreCancelMarkerPlantedByOthers() {
        // If cancel arrives before register, there is no owner to compare yet. The marker is checked when the task is registered.
        when(cancelBucket.get()).thenReturn(OTHER_ID);
        AtomicInteger finalized = new AtomicInteger();

        taskManager.register(TASK_ID, OWNER_ID, finalized::incrementAndGet);

        assertThat(finalized.get()).isZero();
    }

    @Test
    void shouldHonorOwnMarkerPlantedBeforeRegister() {
        when(cancelBucket.get()).thenReturn(OWNER_ID);
        AtomicInteger finalized = new AtomicInteger();

        taskManager.register(TASK_ID, OWNER_ID, finalized::incrementAndGet);

        assertThat(finalized.get()).isOne();
    }

    @Test
    void shouldHonorSystemCancelMarker() {
        // SSE timeout and disconnect run on a container callback thread, which has no signed-in user to compare.
        when(cancelBucket.get()).thenReturn("__system__");
        AtomicInteger finalized = new AtomicInteger();

        taskManager.register(TASK_ID, OWNER_ID, finalized::incrementAndGet);

        assertThat(finalized.get()).isOne();
    }

    @Test
    void shouldCancelSystemTaskLocallyWithoutRedisRoundTrip() {
        AtomicInteger interrupted = new AtomicInteger();
        AtomicInteger finalized = new AtomicInteger();
        taskManager.register(TASK_ID, OWNER_ID, finalized::incrementAndGet);
        taskManager.bindHandle(TASK_ID, interrupted::incrementAndGet);
        taskManager.cancel(TASK_ID);
        taskManager.cancel(TASK_ID);
        assertThat(interrupted.get()).isOne();
        assertThat(finalized.get()).isOne();
        verify(cancelBucket, never()).set(anyString(), any(Duration.class));
        verify(topic, never()).publish(any());
    }

    @Test
    void shouldDropBroadcastCancelFromNonOwner() {
        AtomicInteger finalized = new AtomicInteger();
        taskManager.register(TASK_ID, OWNER_ID, finalized::incrementAndGet);

        captureListener().onMessage(CANCEL_TOPIC, TASK_ID + "|" + OTHER_ID);

        // The publisher check cannot stop a cancel that races ahead of the owner record. The executor must compare again.
        assertThat(finalized.get()).isZero();
        // Do not set the flag either. Setting it short-circuits the register-time marker check.
        assertThat(taskManager.isCancelled(TASK_ID)).isFalse();
    }

    @Test
    void shouldRunBroadcastCancelFromOwner() {
        AtomicInteger finalized = new AtomicInteger();
        taskManager.register(TASK_ID, OWNER_ID, finalized::incrementAndGet);

        captureListener().onMessage(CANCEL_TOPIC, TASK_ID + "|" + OWNER_ID);

        assertThat(finalized.get()).isOne();
        assertThat(taskManager.isCancelled(TASK_ID)).isTrue();
    }

    @Test
    void shouldTreatLegacyPayloadAsSystemCancel() {
        AtomicInteger finalized = new AtomicInteger();
        taskManager.register(TASK_ID, OWNER_ID, finalized::incrementAndGet);

        // During a rolling upgrade an old node broadcasts a bare taskId, and it already checked the owner before publishing.
        captureListener().onMessage(CANCEL_TOPIC, TASK_ID);

        assertThat(finalized.get()).isOne();
    }

    @Test
    void shouldNotResurrectTaskAfterUnregister() {
        AtomicInteger cancelled = new AtomicInteger();
        taskManager.register(TASK_ID, OWNER_ID, () -> {
        });
        taskManager.unregister(TASK_ID);

        // When the stream finishes, unregister runs before bindHandle. That must not invent a ghost task.
        taskManager.bindHandle(TASK_ID, cancelled::incrementAndGet);
        captureListener().onMessage(CANCEL_TOPIC, TASK_ID + "|__system__");

        assertThat(taskManager.isCancelled(TASK_ID)).isFalse();
        assertThat(cancelled.get()).isZero();
    }

    /**
     * Captures the listener registered at subscribe time and uses it to simulate a cancel broadcast arriving on this node.
     */
    @SuppressWarnings("unchecked")
    private MessageListener<String> captureListener() {
        taskManager.subscribe();
        ArgumentCaptor<MessageListener<String>> captor = ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(String.class), captor.capture());
        return captor.getValue();
    }

    private void login(String userId) {
        UserContext.set(LoginUser.builder().userId(userId).build());
    }
}
