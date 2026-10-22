package com.nageoffer.ai.ragent.framework.mq.producer;

/**
 * An outbox record waiting to be delivered. payload is the already serialized message wrapper.
 */
public record OutboxMessage(long id, String topic, String messageKey, String payload, String traceHeaders) {
}
