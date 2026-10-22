package com.nageoffer.ai.ragent.framework.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Idempotency annotation that stops a queue consumer from processing the same message twice.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentConsume {

    /**
     * Prefix for the deduplication key.
     */
    String keyPrefix() default "";

    /**
     * Unique key produced from a SpEL expression.
     */
    String key();

    /**
     * Deduplication key TTL in seconds. Defaults to one hour.
     */
    long keyTimeout() default 3600L;
}
