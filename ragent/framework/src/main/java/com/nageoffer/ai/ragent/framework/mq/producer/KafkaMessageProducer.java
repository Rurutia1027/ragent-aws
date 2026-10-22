package com.nageoffer.ai.ragent.framework.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.mq.MessageWrapper;
import com.nageoffer.ai.ragent.framework.mq.MqTraceHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Kafka producer. acks=all, so a send waits for replica acknowledgement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageProducer implements MessageQueueProducer {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxStore outboxStore;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void send(String topic, String keys, String bizDesc, Object body) {
        String messageKey = messageKey(keys);
        String payload = write(MessageWrapper.builder().keys(messageKey).body(body).build());
        dispatch(topic, messageKey, bizDesc, payload, MqTraceHeaders.write(objectMapper, MqTraceHeaders.capture()));
        log.info("[producer] {} - sent, topic: {}, keys: {}", bizDesc, topic, messageKey);
    }

    @Override
    public void sendInTransaction(String topic, String keys, String bizDesc, Object body,
                                  Consumer<Object> localTransaction) {
        String messageKey = messageKey(keys);
        String traceHeaders = MqTraceHeaders.write(objectMapper, MqTraceHeaders.capture());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            localTransaction.accept(null);
            outboxStore.insert(topic, messageKey, bizDesc,
                    write(MessageWrapper.builder().keys(messageKey).body(body).build()), traceHeaders);
        });
        log.info("[producer] {} - written to the outbox, topic: {}, keys: {}", bizDesc, topic, messageKey);
    }

    private void dispatch(String topic, String messageKey, String bizDesc, String payload, String traceHeaders) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, messageKey, payload,
                MqTraceHeaders.kafkaHeaders(objectMapper, traceHeaders));
        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("[producer] {} - send interrupted, topic: {}, keys: {}", bizDesc, topic, messageKey, ex);
            throw new IllegalStateException("Kafka send interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            log.error("[producer] {} - send failed, topic: {}, keys: {}", bizDesc, topic, messageKey, ex);
            throw new IllegalStateException("Kafka send failed", ex);
        }
    }

    private String write(MessageWrapper<Object> wrapper) {
        try {
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize message", ex);
        }
    }

    private static String messageKey(String keys) {
        return StrUtil.isEmpty(keys) ? UUID.randomUUID().toString() : keys;
    }
}
