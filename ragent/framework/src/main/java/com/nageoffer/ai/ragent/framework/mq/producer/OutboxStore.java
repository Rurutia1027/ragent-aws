package com.nageoffer.ai.ragent.framework.mq.producer;

import java.util.List;

/**
 * Outbox. The insert must run on the caller's transaction thread so it commits or rolls back with that transaction.
 */
public interface OutboxStore {

    void insert(String topic, String messageKey, String bizDesc, String payload, String traceHeaders);

    /**
     * Claims a batch of pending records. A record stuck in sending for more than two minutes after a crash is claimed again.
     */
    List<OutboxMessage> claim(int limit);

    void markSent(long id);

    void release(long id);
}
