package com.nageoffer.ai.ragent.framework.mq;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Rebuilds a typed message wrapper from a Kafka record.
 */
public final class KafkaMessages {

    private KafkaMessages() {
    }

    public static <T> MessageWrapper<T> read(ObjectMapper objectMapper, String payload, Class<T> bodyType) throws IOException {
        JavaType messageType = objectMapper.getTypeFactory()
                .constructParametricType(MessageWrapper.class, bodyType);
        return objectMapper.readValue(payload, messageType);
    }
}
