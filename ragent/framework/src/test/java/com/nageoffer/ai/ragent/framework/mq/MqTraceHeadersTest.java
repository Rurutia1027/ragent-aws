package com.nageoffer.ai.ragent.framework.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MqTraceHeadersTest {

    @AfterEach
    void clearTrace() {
        RagTraceContext.clear();
    }

    @Test
    void restorePutsTheSendingContextBack() {
        RagTraceContext.setTraceId("trace-1");
        RagTraceContext.setTaskId("task-1");
        RagTraceContext.pushNode("node-1");
        String stored = MqTraceHeaders.write(new ObjectMapper(), MqTraceHeaders.capture());
        RagTraceContext.clear();

        MqTraceHeaders.restore(MqTraceHeaders.kafkaHeaders(new ObjectMapper(), stored));

        assertEquals("trace-1", RagTraceContext.getTraceId());
        assertEquals("task-1", RagTraceContext.getTaskId());
        assertEquals("node-1", RagTraceContext.currentNodeId());
    }

    @Test
    void missingHeadersLeaveTheContextEmpty() {
        MqTraceHeaders.restore(MqTraceHeaders.kafkaHeaders(new ObjectMapper(), null));

        assertNull(RagTraceContext.getTraceId());
        assertNull(RagTraceContext.currentNodeId());
    }
}
