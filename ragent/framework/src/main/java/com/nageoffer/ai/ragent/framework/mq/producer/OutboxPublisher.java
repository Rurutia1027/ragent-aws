package com.nageoffer.ai.ragent.framework.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.mq.MqTraceHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends committed outbox records to Kafka. A failed send is returned to pending and retried on the next pass.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int BATCH_SIZE = 20;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxStore outboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${ragent.mq.outbox-delay-ms:1000}")
    public void publish() {
        List<OutboxMessage> batch;
        try {
            batch = outboxStore.claim(BATCH_SIZE);
        } catch (RuntimeException ex) {
            log.error("[outbox] failed to claim pending records", ex);
            return;
        }
        for (OutboxMessage message : batch) {
            publishOne(message);
        }
    }

    private void publishOne(OutboxMessage message) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    message.topic(), null, message.messageKey(), message.payload(),
                    MqTraceHeaders.kafkaHeaders(objectMapper, message.traceHeaders()));
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outboxStore.markSent(message.id());
            log.info("[outbox] delivered, topic: {}, keys: {}, id: {}", message.topic(), message.messageKey(), message.id());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            release(message, ex);
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            release(message, ex);
        }
    }

    private void release(OutboxMessage message, Exception ex) {
        log.error("[outbox] delivery failed, returned to pending, topic: {}, keys: {}, id: {}",
                message.topic(), message.messageKey(), message.id(), ex);
        try {
            outboxStore.release(message.id());
        } catch (RuntimeException releaseError) {
            log.error("[outbox] failed to return the record to pending, id: {}", message.id(), releaseError);
        }
    }
}
