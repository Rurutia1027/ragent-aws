package com.nageoffer.ai.ragent.framework.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Question constraint shared by the RAG and agent entry points.
 * The cap follows a GET query string. A CJK character URL-encodes to about 9 bytes, and a longer value hits the container's 8KB header limit first.
 */
@Documented
@NotBlank(message = "Question cannot be empty")
@Size(max = ChatQuestion.MAX_LENGTH, message = "Question is too long. Maximum " + ChatQuestion.MAX_LENGTH + " characters")
@Constraint(validatedBy = {})
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ChatQuestion {

    int MAX_LENGTH = 500;

    String message() default "The question is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
