package com.nageoffer.ai.ragent.framework.cache;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisKeySerializerTest {

    @Test
    void serializesKeysWithUtf8() throws Exception {
        RedisKeySerializer serializer = new RedisKeySerializer();
        Field keyPrefix = RedisKeySerializer.class.getDeclaredField("keyPrefix");
        keyPrefix.setAccessible(true);
        keyPrefix.set(serializer, "cache:");

        byte[] encoded = serializer.serialize("knowledge-base");

        assertArrayEquals("cache:knowledge-base".getBytes(StandardCharsets.UTF_8), encoded);
        assertEquals("cache:knowledge-base", serializer.deserialize(encoded));
    }
}
