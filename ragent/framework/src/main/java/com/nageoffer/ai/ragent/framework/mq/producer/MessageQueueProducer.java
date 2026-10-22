package com.nageoffer.ai.ragent.framework.mq.producer;

import java.util.function.Consumer;

/**
 * Message queue producer.
 * A normal send writes Kafka directly. A send that must commit with the local database is written to the outbox and delivered to Kafka afterwards.
 */
public interface MessageQueueProducer {

    /**
     * Direct send. Use this for messages, such as feedback, that do not need to commit with a local transaction.
     *
     * @param topic   destination topic
     * @param keys    business key, also used as the partition key
     * @param bizDesc business description, used in logs
     * @param body    business payload
     */
    void send(String topic, String keys, String bizDesc, Object body);

    /**
     * Commits together with the local transaction.
     * The local transaction and the outbox insert share one database transaction. The publisher sends to Kafka only after commit.
     * If the local transaction throws, the outbox keeps no record.
     *
     * @param topic            destination topic
     * @param keys             business key
     * @param bizDesc          business description
     * @param body             business payload. Mutations made inside the local transaction are what get stored.
     * @param localTransaction local transaction callback
     */
    void sendInTransaction(String topic, String keys, String bizDesc, Object body,
                           Consumer<Object> localTransaction);
}
