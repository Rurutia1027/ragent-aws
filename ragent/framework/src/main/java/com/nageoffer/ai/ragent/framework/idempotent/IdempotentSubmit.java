package com.nageoffer.ai.ragent.framework.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Idempotency annotation that stops a user from submitting the same form twice.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentSubmit {

    /**
     * Unique key from a SpEL expression. Takes precedence over the default idempotency key.
     */
    String key() default "";

    /**
     * Message returned when the idempotency check fails.
     */
    String message() default "You are submitting too quickly. Please try again shortly.";
}
