package com.nageoffer.ai.ragent.framework.mq;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Puts the current RAG trace into Kafka headers and restores it into {@link RagTraceContext} on consume.
 */
public final class MqTraceHeaders {

    public static final String TRACE_ID = "ragent-trace-id";
    public static final String TASK_ID = "ragent-task-id";
    public static final String PARENT_NODE_ID = "ragent-parent-node-id";

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private MqTraceHeaders() {
    }

    public static Map<String, String> capture() {
        Map<String, String> headers = new LinkedHashMap<>();
        put(headers, TRACE_ID, RagTraceContext.getTraceId());
        put(headers, TASK_ID, RagTraceContext.getTaskId());
        put(headers, PARENT_NODE_ID, RagTraceContext.currentNodeId());
        return headers;
    }

    public static String write(ObjectMapper objectMapper, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize trace headers", ex);
        }
    }

    public static Headers kafkaHeaders(ObjectMapper objectMapper, String json) {
        RecordHeaders headers = new RecordHeaders();
        if (StrUtil.isBlank(json)) {
            return headers;
        }
        try {
            Map<String, String> values = objectMapper.readValue(json, MAP_TYPE);
            values.forEach((key, value) -> headers.add(header(key, value)));
        } catch (Exception ex) {
            return new RecordHeaders();
        }
        return headers;
    }

    public static void restore(Headers headers) {
        RagTraceContext.clear();
        if (headers == null) {
            return;
        }
        String traceId = value(headers, TRACE_ID);
        String taskId = value(headers, TASK_ID);
        String parentNodeId = value(headers, PARENT_NODE_ID);
        if (StrUtil.isNotBlank(traceId)) {
            RagTraceContext.setTraceId(traceId);
        }
        if (StrUtil.isNotBlank(taskId)) {
            RagTraceContext.setTaskId(taskId);
        }
        if (StrUtil.isNotBlank(parentNodeId)) {
            RagTraceContext.pushNode(parentNodeId);
        }
    }

    private static void put(Map<String, String> headers, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            headers.put(key, value);
        }
    }

    private static Header header(String key, String value) {
        return new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
