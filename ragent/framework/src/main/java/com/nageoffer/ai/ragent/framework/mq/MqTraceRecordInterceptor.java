package com.nageoffer.ai.ragent.framework.mq;

import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Restores the trace from headers before consumption and clears it afterwards so it does not leak into the next record.
 */
public class MqTraceRecordInterceptor implements RecordInterceptor<String, String> {

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        MqTraceHeaders.restore(record.headers());
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        RagTraceContext.clear();
    }
}
