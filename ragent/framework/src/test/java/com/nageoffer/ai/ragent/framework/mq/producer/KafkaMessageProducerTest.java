package com.nageoffer.ai.ragent.framework.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.mq.MqTraceHeaders;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaMessageProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxStore outboxStore;
    private KafkaMessageProducer producer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        outboxStore = mock(OutboxStore.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        producer = new KafkaMessageProducer(kafkaTemplate, outboxStore, new ObjectMapper(), transactionManager);
    }

    @AfterEach
    void clearTrace() {
        RagTraceContext.clear();
    }

    @Test
    void directSendWaitsForKafka() {
        RagTraceContext.setTraceId("trace-1");
        RagTraceContext.setTaskId("task-1");

        producer.send("message-feedback_topic", "user:1", "message feedback", "up");

        ProducerRecord<String, String> record = sentRecord();
        assertEquals("message-feedback_topic", record.topic());
        assertEquals("user:1", record.key());
        assertTrue(record.value().contains("\"up\""));
        assertEquals("trace-1", header(record, MqTraceHeaders.TRACE_ID));
        assertEquals("task-1", header(record, MqTraceHeaders.TASK_ID));
        verify(outboxStore, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void transactionalSendWritesOutboxAfterLocalChange() {
        AtomicBoolean ran = new AtomicBoolean();

        producer.sendInTransaction("knowledge-document-chunk_topic", "doc-1", "document chunk", new Payload(), ignored -> {
            ran.set(true);
        });

        assertTrue(ran.get());
        verify(outboxStore).insert(eq("knowledge-document-chunk_topic"), eq("doc-1"), eq("document chunk"), contains("doc-1"), isNull());
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void localFailureDoesNotWriteOutbox() {
        IllegalStateException failure = new IllegalStateException("already running");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                producer.sendInTransaction("knowledge-document-chunk_topic", "doc-1", "document chunk", "body", ignored -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        verify(outboxStore, never()).insert(any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> sentRecord() {
        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    private static String header(ProducerRecord<String, String> record, String key) {
        return new String(record.headers().lastHeader(key).value(), StandardCharsets.UTF_8);
    }

    private static final class Payload {
        public String docId = "doc-1";
    }
}
