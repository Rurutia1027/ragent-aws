package com.nageoffer.ai.ragent.framework.web;

import cn.hutool.core.util.StrUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-node cancellation manager for streaming tasks.
 * <p>
 * The mechanism is engine-agnostic. System cancellation updates the local registry. A user stop is delivered across nodes by a Redis marker and a broadcast.
 * Engine-specific cleanup, such as emitting a terminal event or interrupting the upstream stream, is injected as a callback.
 */
@Slf4j
@Component
public class StreamTaskManager {

    private static final String CANCEL_TOPIC = "ragent:stream:cancel";
    private static final String CANCEL_KEY_PREFIX = "ragent:stream:cancel:";
    private static final String OWNER_KEY_PREFIX = "ragent:stream:owner:";
    private static final Duration CANCEL_TTL = Duration.ofMinutes(30);

    /**
     * Placeholder requester for system reclamation. It cannot collide with a user id, which is a numeric snowflake.
     */
    private static final String SYSTEM_REQUESTER = "__system__";

    /**
     * Separator for the taskId|requester broadcast payload. Both ids are numeric snowflakes and never contain a pipe.
     */
    private static final String PAYLOAD_SEPARATOR = "|";

    private final Cache<String, StreamTaskInfo> tasks = CacheBuilder.newBuilder()
            .expireAfterWrite(CANCEL_TTL)
            .maximumSize(10000)  // Upper bound. It is not expected to be reached; raise it in configuration if that assumption changes.
            .build();

    private final RedissonClient redissonClient;
    private int listenerId = -1;

    public static Duration taskRetention() {
        return CANCEL_TTL;
    }

    public StreamTaskManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void subscribe() {
        RTopic topic = redissonClient.getTopic(CANCEL_TOPIC);
        listenerId = topic.addListener(String.class, (channel, payload) -> {
            if (StrUtil.isBlank(payload)) {
                return;
            }
            int separator = payload.indexOf(PAYLOAD_SEPARATOR);
            // During a rolling upgrade an old node still broadcasts a bare taskId. It already checked the owner, so treat it as a system cancel.
            String taskId = separator < 0 ? payload : payload.substring(0, separator);
            String requester = separator < 0 ? SYSTEM_REQUESTER : payload.substring(separator + 1);
            cancelLocal(taskId, requester);
        });
    }

    @PreDestroy
    public void unsubscribe() {
        if (listenerId == -1) {
            return;
        }
        redissonClient.getTopic(CANCEL_TOPIC).removeListener(listenerId);
    }

    /**
     * Registers the cancel finalizer and records the owner. The callback emits the terminal event and closes the response stream.
     * If the owner or the system has already marked the task cancelled, the callback runs immediately.
     */
    public void register(String taskId, String ownerUserId, Runnable onCancelFinalizer) {
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        // The owner must be stored before the finalizer. Otherwise a broadcast arriving between the two assignments can kill an unowned stream.
        taskInfo.ownerUserId = ownerUserId;
        taskInfo.finalizer = onCancelFinalizer;
        // The owner is stored in Redis, not only locally, because the stop request may hit a node that is not running the stream.
        if (StrUtil.isNotBlank(ownerUserId)) {
            RBucket<String> owner = redissonClient.getBucket(ownerKey(taskId));
            owner.set(ownerUserId, CANCEL_TTL);
        }
        if (isTaskCancelledInRedis(taskId, taskInfo)) {
            onCancelFinalizer.run();
        }
    }

    /**
     * Binds the action that interrupts the upstream stream. It runs before the finalizer on cancel.
     * If the task was already unregistered, do not create a record. That would leave a ghost task nobody fires.
     */
    public void bindHandle(String taskId, Runnable cancelAction) {
        StreamTaskInfo taskInfo = tasks.getIfPresent(taskId);
        if (taskInfo == null) {
            return;
        }
        taskInfo.cancelAction = cancelAction;
        if (taskInfo.cancelled.get() && cancelAction != null) {
            cancelAction.run();
        }
    }

    public boolean isCancelled(String taskId) {
        StreamTaskInfo info = tasks.getIfPresent(taskId);
        return info != null && info.cancelled.get();
    }

    /**
     * System reclamation for SSE timeout or client disconnect. The container callback thread has no signed-in user to compare.
     */
    public void cancel(String taskId) {
        // The SSE lifecycle callback and the task share a JVM, so this does not need a Redis or MQ round trip.
        cancelLocal(taskId, SYSTEM_REQUESTER);
    }

    /**
     * User stop. taskId is a time-ordered snowflake, not an access token, so the owner must be compared.
     */
    public void cancelByUser(String taskId) {
        String requester = UserContext.requireUser().getUserId();
        RBucket<String> owner = redissonClient.getBucket(ownerKey(taskId));
        String ownerUserId = owner.get();
        if (StrUtil.isNotBlank(ownerUserId) && !ownerUserId.equals(requester)) {
            log.warn("Rejected an unauthorized stop of a stream task, taskId: {}, owner: {}, requester: {}", taskId, ownerUserId, requester);
            // Do not distinguish missing from not-owned, or the stop API becomes a probe for someone else's task.
            throw new ClientException("Task does not exist or has already finished");
        }
        // A missing owner usually means the task already finished, but registration may not have landed yet. The marker carries the requester and is checked at register time.
        publishCancel(taskId, requester);
    }

    private void publishCancel(String taskId, String requester) {
        // Set the Redis marker first, then publish.
        RBucket<String> bucket = redissonClient.getBucket(cancelKey(taskId));
        bucket.set(requester, CANCEL_TTL);

        // Publish so every node, including this one, is notified.
        // The local node is handled by the same listener, so cancelLocal is not called twice.
        // The payload carries the requester so the executor can re-check. A publisher-only check cannot stop a race before the owner is stored.
        redissonClient.getTopic(CANCEL_TOPIC).publish(taskId + PAYLOAD_SEPARATOR + requester);
    }

    /**
     * If Redis already marks the task cancelled, copy that state into the local cache.
     * The marker may arrive before registration, when there is no owner to compare. The check is deferred until here.
     */
    private boolean isTaskCancelledInRedis(String taskId, StreamTaskInfo taskInfo) {
        if (taskInfo.cancelled.get()) {
            return true;
        }

        RBucket<String> bucket = redissonClient.getBucket(cancelKey(taskId));
        String requester = bucket.get();
        if (requester == null) {
            return false;
        }
        if (isRequesterDenied(taskInfo, requester)) {
            log.warn("Ignored a cancel marker planted by a non-owner, taskId: {}, owner: {}, requester: {}", taskId, taskInfo.ownerUserId, requester);
            return false;
        }
        taskInfo.cancelled.set(true);
        return true;
    }

    /**
     * Whether to reject the requester. System reclamation is allowed. A user who is not the owner, or whose owner record is not stored yet, is rejected.
     * This is the window the planted marker is supposed to be re-checked at register time. Letting it through locally skips that check.
     */
    private boolean isRequesterDenied(StreamTaskInfo taskInfo, String requester) {
        if (SYSTEM_REQUESTER.equals(requester)) {
            return false;
        }
        return StrUtil.isBlank(taskInfo.ownerUserId) || !taskInfo.ownerUserId.equals(requester);
    }

    private void cancelLocal(String taskId, String requester) {
        StreamTaskInfo taskInfo = tasks.getIfPresent(taskId);
        if (taskInfo == null) {
            return;
        }

        // The executor re-checks the requester. taskId is time-ordered, so a guessed id must not cancel the task.
        // On a mismatch, do not even set cancelled. That would short-circuit the register-time check.
        if (isRequesterDenied(taskInfo, requester)) {
            log.warn("Rejected an unauthorized cancel of a stream task, taskId: {}, owner: {}, requester: {}", taskId, taskInfo.ownerUserId, requester);
            return;
        }

        // CAS so the cancel runs only once.
        if (!taskInfo.cancelled.compareAndSet(false, true)) {
            return;
        }

        if (taskInfo.cancelAction != null) {
            taskInfo.cancelAction.run();
        }

        // Run the finalizer on cancel so content accumulated so far is saved.
        if (taskInfo.finalizer != null) {
            taskInfo.finalizer.run();
        }
    }

    public void unregister(String taskId) {
        // Clear the local cache.
        tasks.invalidate(taskId);

        // Clear Redis.
        redissonClient.getBucket(cancelKey(taskId)).deleteAsync();
        redissonClient.getBucket(ownerKey(taskId)).deleteAsync();
    }

    private String cancelKey(String taskId) {
        return CANCEL_KEY_PREFIX + taskId;
    }

    private String ownerKey(String taskId) {
        return OWNER_KEY_PREFIX + taskId;
    }

    @SneakyThrows
    private StreamTaskInfo getOrCreate(String taskId) {
        return tasks.get(taskId, StreamTaskInfo::new);
    }

    private static final class StreamTaskInfo {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile String ownerUserId;
        private volatile Runnable cancelAction;
        private volatile Runnable finalizer;
    }
}
