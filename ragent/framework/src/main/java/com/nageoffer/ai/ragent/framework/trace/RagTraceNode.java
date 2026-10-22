package com.nageoffer.ai.ragent.framework.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an ordinary node on the RAG trace.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RagTraceNode {

    /**
     * Node name, used for display.
     */
    String name() default "";

    /**
     * Node type, used for grouped statistics.
     */
    String type() default "METHOD";
}
