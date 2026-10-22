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

package com.nageoffer.ai.ragent.rag.service.ratelimit;

import cn.hutool.core.util.IdUtil;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Distributed fair rate limiter
 */
@Slf4j
public final class FairDistributedRateLimiter {

    private static final String LUA_PATH = "lua/queue_claim_atomic.lua";
    /**
     * Extra buffer on the entry TTL above maxWaitMillis, so millisecond clock skew does not mark a live entry as a zombie
     */
    private static final long ENTRY_TTL_BUFFER_MILLIS = 5_000L;

    private final String name;
    private final RedissonClient redissonClient;
    private final IntSupplier maxPermitsSupplier;
    private final IntSupplier leaseSecondsSupplier;
    private final IntSupplier pollIntervalMsSupplier;

    private final String semaphoreKey;
    private final String queueKey;
    private final String queueSeqKey;
    private final String notifyTopicKey;
    private final String entryKeyPrefix;
    private final String claimLua;

    private final ScheduledExecutorService scheduler;
    private final PollNotifier pollNotifier;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile int notifyListenerId = -1;

    public FairDistributedRateLimiter(String name,
                                      RedissonClient redissonClient,
                                      IntSupplier maxPermitsSupplier,
                                      IntSupplier leaseSecondsSupplier,
                                      IntSupplier pollIntervalMsSupplier) {
        this.name = Objects.requireNonNull(name);
        this.redissonClient = Objects.requireNonNull(redissonClient);
        this.maxPermitsSupplier = Objects.requireNonNull(maxPermitsSupplier);
        this.leaseSecondsSupplier = Objects.requireNonNull(leaseSecondsSupplier);
        this.pollIntervalMsSupplier = Objects.requireNonNull(pollIntervalMsSupplier);

        this.semaphoreKey = name + ":semaphore";
        this.queueKey = name + ":queue";
        this.queueSeqKey = name + ":queue:seq";
        this.notifyTopicKey = name + ":queue:notify";
        this.entryKeyPrefix = name + ":entry:";
        this.claimLua = loadLuaScript();

        String threadPrefix = name.replace(':', '_');
        int schedulerSize = Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        AtomicInteger threadCounter = new AtomicInteger();
        this.scheduler = new ScheduledThreadPoolExecutor(schedulerSize, r -> {
            Thread t = new Thread(r);
            t.setName(threadPrefix + "_scheduler_" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.pollNotifier = new PollNotifier(this::availablePermits, scheduler);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        // Initialize the semaphore permit count once. trySetPermits is itself idempotent and applies only the first time
        // Later acquire/availablePermits calls do not repeat it, so each poller avoids an extra Redis round trip
        redissonClient.getPermitExpirableSemaphore(semaphoreKey).trySetPermits(maxPermitsSupplier.getAsInt());
        RTopic topic = redissonClient.getTopic(notifyTopicKey);
        notifyListenerId = topic.addListener(String.class, (channel, msg) -> pollNotifier.fire());
    }

    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        if (notifyListenerId != -1) {
            redissonClient.getTopic(notifyTopicKey).removeListener(notifyListenerId);
            notifyListenerId = -1;
        }
        scheduler.shutdown();
        awaitShutdown(scheduler);
        pollNotifier.clear();
    }

    /**
     * Queue for a permit without blocking
     */
    public void acquire(AcquireRequest req) {
        Ticket ticket = new Ticket(req);
        if (req.cancelBinder() != null) {
            req.cancelBinder().accept(ticket::cancel);
        }
        // The entry liveness mark must be written before enqueue, or a concurrent claim in the race window ZREMs the just-enqueued entry as a zombie
        setEntryMarker(ticket.requestId, req.maxWaitMillis());
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(queueKey, StringCodec.INSTANCE);
        queue.add(nextQueueSeq(), ticket.requestId);
        if (tryAcquireIfReady(ticket)) {
            return;
        }
        scheduleQueuePoll(ticket);
    }

    // ==================== Ticket state machine ====================

    /**
     * Single CAS coordination point. Terminal states are exclusive: once the state leaves PENDING it does not change, and the business callback fires at most once.
     * Resource cleanup ({@link Ticket#cleanup()}) is decoupled from the state machine and runs idempotently
     */
    private enum State {PENDING, GRANTED, TIMED_OUT, CANCELLED}

    private final class Ticket {
        final String requestId = IdUtil.getSnowflakeNextIdStr();
        final long deadline;
        final AcquireRequest req;
        final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        final AtomicReference<String> permitRef = new AtomicReference<>();
        volatile ScheduledFuture<?> future;

        Ticket(AcquireRequest req) {
            this.req = req;
            this.deadline = System.currentTimeMillis() + req.maxWaitMillis();
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        boolean isPending() {
            return state.get() == State.PENDING;
        }

        /**
         * External cancel (emitter complete / timeout / error): idempotently release queue resources and unregister, and best-effort suppress the business callback
         *
         * <p>Do not release the permit in the GRANTED state. {@link #grant} already wrapped it in try/finally,
         * and the permit is released in finally when the business work finishes (including on exception). Releasing it here would hand a still-in-use permit to a concurrent request
         */
        void cancel() {
            state.compareAndSet(State.PENDING, State.CANCELLED);
            cleanup();
        }

        /**
         * Queue timeout: after CAS takes the terminal state, cleanup and run onTimeout on the caller executor
         */
        void timeout() {
            if (!state.compareAndSet(State.PENDING, State.TIMED_OUT)) {
                return;
            }
            cleanup();
            submitSafely(req.onTimeout(), "onTimeout");
        }

        /**
         * Permit acquired. After CAS takes the GRANTED terminal state, the permit lifetime is handed to the business work (released in try/finally)
         *
         * <p>permitRef is set before the CAS. A concurrent cancel/timeout that loses the CAS
         * can still see the permit and release it, so a grant/cancel race does not leak the permit
         */
        boolean grant(String permitId) {
            permitRef.set(permitId);
            if (!state.compareAndSet(State.PENDING, State.GRANTED)) {
                // Already taken by cancel/timeout. permitRef may already have been cleared by the other side's cleanup; CAS prevents a double release
                if (permitRef.compareAndSet(permitId, null)) {
                    releasePermitQuietly(permitId);
                    publishQueueNotify();
                }
                return false;
            }
            unregisterFromNotifier();
            cancelFutureQuietly();
            Runnable wrapped = () -> {
                try {
                    req.onAcquired().run();
                } finally {
                    releaseHeldPermit();
                }
            };
            try {
                req.onAcquiredExecutor().execute(wrapped);
                return true;
            } catch (RejectedExecutionException ex) {
                log.warn("[{}] onAcquired 提交失败，降级为 timeout 拒绝路径", name, ex);
                releaseHeldPermit();   // The business work did not run, so the permit must be released explicitly (cleanup does not release a permit in the GRANTED state)
                cleanup();
                submitSafely(req.onTimeout(), "onTimeout(fallback)");
                return false;
            }
        }

        /**
         * Release the permit held by this Ticket, if any. Thread-safe and idempotent
         */
        void releaseHeldPermit() {
            String pid = permitRef.getAndSet(null);
            if (pid != null) {
                releasePermitQuietly(pid);
                publishQueueNotify();
            }
        }

        /**
         * Idempotent cleanup: leave the queue, delete the entry mark, release the permit (only when not GRANTED), unregister the poller, and cancel the future
         *
         * <p>In the GRANTED state the permit is owned by the Runnable wrapped in grant, and cleanup does not release it.
         * Otherwise the permit would return to the semaphore while the business work is still running, giving the same slot to another request
         */
        void cleanup() {
            boolean removed = false;
            try {
                removed = redissonClient.getScoredSortedSet(queueKey, StringCodec.INSTANCE).remove(requestId);
            } catch (Exception ex) {
                log.debug("[{}] 移除队列失败 (requestId={})", name, requestId, ex);
            }
            deleteEntryMarker(requestId);

            boolean releasedPermit = false;
            if (state.get() != State.GRANTED) {
                String permitId = permitRef.getAndSet(null);
                if (permitId != null) {
                    releasePermitQuietly(permitId);
                    releasedPermit = true;
                }
            }
            if (removed || releasedPermit) {
                publishQueueNotify();
            }
            unregisterFromNotifier();
            cancelFutureQuietly();
        }

        void unregisterFromNotifier() {
            pollNotifier.unregister(requestId);
        }

        void cancelFutureQuietly() {
            ScheduledFuture<?> f = future;
            if (f != null && !f.isCancelled()) {
                f.cancel(false);
            }
        }

        private void submitSafely(Runnable r, String label) {
            try {
                req.onAcquiredExecutor().execute(r);
            } catch (Exception ex) {
                log.warn("[{}] {} 提交失败，回调被丢弃", name, label, ex);
            }
        }
    }

    // ==================== Claim core ====================

    private boolean tryAcquireIfReady(Ticket ticket) {
        if (!ticket.isPending()) {
            return false;
        }
        int avail = availablePermits();
        if (avail <= 0) {
            return false;
        }
        long claimedScore = claimIfReady(ticket.requestId, avail);
        if (claimedScore < 0L) {
            return false;
        }
        String permitId = tryAcquirePermit();
        if (permitId == null) {
            // Head of the queue but no permit: re-enqueue at the original score and keep the queue position (fairness)
            // Race with cancel/timeout: claimIfReady already ZREMed, so cleanup's remove is a no-op at this moment;
            // After add, re-read state. If it is already terminal, roll back, so a zombie entry does not hold the head window forever
            setEntryMarker(ticket.requestId, Math.max(1, ticket.deadline - System.currentTimeMillis()));
            RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(queueKey, StringCodec.INSTANCE);
            queue.add(claimedScore, ticket.requestId);
            publishQueueNotify();
            if (!ticket.isPending()) {
                queue.remove(ticket.requestId);
                deleteEntryMarker(ticket.requestId);
            }
            return false;
        }
        if (!ticket.isPending()) {
            // Cancelled or timed out between claim and acquire: the permit must be released and a notification sent, or other waiters wait until the next poll
            releasePermitQuietly(permitId);
            publishQueueNotify();
            return false;
        }
        publishQueueNotify();
        return ticket.grant(permitId);
    }

    private void scheduleQueuePoll(Ticket ticket) {
        int interval = Math.max(50, pollIntervalMsSupplier.getAsInt());
        Runnable poller = () -> {
            if (!ticket.isPending()) {
                ticket.unregisterFromNotifier();
                ticket.cancelFutureQuietly();
                return;
            }
            if (System.currentTimeMillis() > ticket.deadline) {
                ticket.timeout();
                return;
            }
            tryAcquireIfReady(ticket);
        };
        ticket.future = scheduler.scheduleAtFixedRate(poller, interval, interval, TimeUnit.MILLISECONDS);
        pollNotifier.register(ticket.requestId, poller);
    }

    // ==================== Redis operations ====================

    private String tryAcquirePermit() {
        RPermitExpirableSemaphore sem = redissonClient.getPermitExpirableSemaphore(semaphoreKey);
        try {
            return sem.tryAcquire(0, leaseSecondsSupplier.getAsInt(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private int availablePermits() {
        return redissonClient.getPermitExpirableSemaphore(semaphoreKey).availablePermits();
    }

    private void releasePermitQuietly(String permitId) {
        try {
            redissonClient.getPermitExpirableSemaphore(semaphoreKey).release(permitId);
        } catch (Exception ex) {
            log.debug("[{}] 释放 permit 失败（可能已过期）：{}", name, ex.getMessage());
        }
    }

    /**
     * Write the entry liveness mark. TTL = wait budget + buffer. After a JVM crash the key expires on its own,
     * and a later {@link #claimIfReady} drops the matching ZSet entry as a zombie inside Lua so it cannot hold the head window forever
     */
    private void setEntryMarker(String requestId, long remainingMillis) {
        long ttlMillis = Math.max(remainingMillis, 1L) + ENTRY_TTL_BUFFER_MILLIS;
        try {
            RBucket<String> bucket = redissonClient.getBucket(entryKeyPrefix + requestId, StringCodec.INSTANCE);
            bucket.set("1", Duration.ofMillis(ttlMillis));
        } catch (Exception ex) {
            log.debug("[{}] 写入 entry 标记失败 (requestId={})", name, requestId, ex);
        }
    }

    private void deleteEntryMarker(String requestId) {
        try {
            redissonClient.getBucket(entryKeyPrefix + requestId, StringCodec.INSTANCE).delete();
        } catch (Exception ex) {
            log.debug("[{}] 删除 entry 标记失败 (requestId={})", name, requestId, ex);
        }
    }

    /**
     * @return on success, the ticket's original score (so a failure can re-enqueue at the same position); -1 if not claimed
     */
    private long claimIfReady(String requestId, int availablePermits) {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        List<Object> result = script.eval(
                RScript.Mode.READ_WRITE,
                claimLua,
                RScript.ReturnType.LIST,
                List.of(queueKey),
                requestId,
                String.valueOf(availablePermits),
                entryKeyPrefix
        );
        if (result == null || result.isEmpty() || parseLong(result.get(0)) != 1L) {
            return -1L;
        }
        return result.size() >= 2 ? parseLong(result.get(1)) : nextQueueSeq();
    }

    private long nextQueueSeq() {
        RAtomicLong seq = redissonClient.getAtomicLong(queueSeqKey);
        return seq.incrementAndGet();
    }

    private void publishQueueNotify() {
        redissonClient.getTopic(notifyTopicKey).publish("permit_changed");
    }

    // ==================== Helpers ====================

    private static long parseLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static String loadLuaScript() {
        try {
            ClassPathResource resource = new ClassPathResource(LUA_PATH);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load Lua script: " + LUA_PATH, ex);
        }
    }

    private static void awaitShutdown(ScheduledExecutorService exec) {
        try {
            if (!exec.awaitTermination(3, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException ex) {
            exec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Public types ====================

    /**
     * Claim request parameters
     */
    @Builder
    public record AcquireRequest(long maxWaitMillis,
                                 Runnable onAcquired,
                                 Runnable onTimeout,
                                 Executor onAcquiredExecutor,
                                 Consumer<Runnable> cancelBinder) {
        public AcquireRequest {
            Objects.requireNonNull(onAcquired);
            Objects.requireNonNull(onTimeout);
            Objects.requireNonNull(onAcquiredExecutor);
            if (maxWaitMillis <= 0) {
                throw new IllegalArgumentException("maxWaitMillis must be > 0");
            }
        }
    }

    // ==================== PollNotifier ====================

    /**
     * After a cross-instance RTopic notification arrives, wake every poller in this process
     *
     * <p>Coalesce with a {@code firing} CAS and a {@code pendingNotifications} count: a burst of notifications
     * triggers one scan, not a storm. The scan reuses the external scheduler, so no dedicated thread is needed
     */
    private static final class PollNotifier {

        private final IntSupplier permitSupplier;
        private final Executor executor;
        private final ConcurrentHashMap<String, Runnable> pollers = new ConcurrentHashMap<>();
        private final AtomicBoolean firing = new AtomicBoolean(false);
        private final AtomicInteger pendingNotifications = new AtomicInteger(0);

        PollNotifier(IntSupplier permitSupplier, Executor executor) {
            this.permitSupplier = permitSupplier;
            this.executor = executor;
        }

        void register(String requestId, Runnable poller) {
            pollers.put(requestId, poller);
        }

        void unregister(String requestId) {
            pollers.remove(requestId);
        }

        void fire() {
            pendingNotifications.incrementAndGet();
            if (!firing.compareAndSet(false, true)) {
                return;
            }
            executor.execute(() -> {
                do {
                    pendingNotifications.set(0);
                    try {
                        if (permitSupplier.getAsInt() <= 0) {
                            // Permits are exhausted, so this round need not scan every poller. The next real release sends a new notification and drives them again
                            break;
                        }
                        for (Runnable poller : pollers.values()) {
                            try {
                                poller.run();
                            } catch (Exception ex) {
                                log.debug("poller 执行异常", ex);
                            }
                        }
                    } finally {
                        firing.set(false);
                    }
                } while (pendingNotifications.get() > 0 && firing.compareAndSet(false, true));
            });
        }

        void clear() {
            pollers.clear();
        }
    }
}
