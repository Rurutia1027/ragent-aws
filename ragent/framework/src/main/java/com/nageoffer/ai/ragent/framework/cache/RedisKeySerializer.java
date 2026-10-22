package com.nageoffer.ai.ragent.framework.cache;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Key Serializer
 */
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "framework.cache.redis.prefix")
public class RedisKeySerializer implements RedisSerializer<String> {
    @Value("${framework.cache.redis.prefix:}")
    private String keyPrefix;


    @Override
    public byte[] serialize(@Nullable String key) throws SerializationException {
        String builderKey = keyPrefix + key;
        return builderKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public @Nullable String deserialize(byte @Nullable [] bytes) throws SerializationException {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
