package com.nageoffer.ai.ragent.framework.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatQuestionTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectBlankQuestion() {
        // An empty question used to reach the agent, after a session had already been opened and persisted.
        assertThat(violations("  ")).singleElement()
                .satisfies(message -> assertThat(message).contains("cannot be empty"));
    }

    @Test
    void shouldRejectQuestionBeyondLimit() {
        // An over-long GET query hits the container header limit first and comes back as an unexplained 400.
        assertThat(violations("Q".repeat(ChatQuestion.MAX_LENGTH + 1))).singleElement()
                .satisfies(message -> assertThat(message).contains("too long"));
    }

    @Test
    void shouldAcceptOrdinaryQuestion() {
        assertThat(violations("How is the Ragent retrieval pipeline ordered?")).isEmpty();
    }

    private List<String> violations(String question) {
        return VALIDATOR.validate(new Holder(question)).stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    private static final class Holder {

        @ChatQuestion
        private final String question;

        private Holder(String question) {
            this.question = question;
        }
    }
}
