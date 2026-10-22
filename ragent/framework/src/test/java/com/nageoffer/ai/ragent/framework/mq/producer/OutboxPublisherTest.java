package com.nageoffer.ai.ragent.framework.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.mq.MqTraceHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    @Test
    void successfulDispatchMarksSent() {
        OutboxStore outboxStore = mock(OutboxStore.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxMessage message = new OutboxMessage(7L, "knowledge-base-cleanup_topic", "kb-1", "{}",
                "{\"" + MqTraceHeaders.TRACE_ID + "\":\"trace-7\"}");
        when(outboxStore.claim(20)).thenReturn(List.of(message));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        new OutboxPublisher(outboxStore, kafkaTemplate, new ObjectMapper()).publish();

        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("trace-7", new String(captor.getValue().headers().lastHeader(MqTraceHeaders.TRACE_ID).value(),
                StandardCharsets.UTF_8));

        verify(outboxStore).markSent(7L);
    }

    @Test
    void failedDispatchReturnsTheRow() {
        OutboxStore outboxStore = mock(OutboxStore.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxMessage message = new OutboxMessage(8L, "knowledge-document-chunk_topic", "doc-1", "{}", null);
        when(outboxStore.claim(20)).thenReturn(List.of(message));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        new OutboxPublisher(outboxStore, kafkaTemplate, new ObjectMapper()).publish();

        verify(outboxStore).release(8L);
    }
}
