package com.nageoffer.ai.ragent.framework.idempotent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * Consumption state for idempotent queue handling.
 */
@RequiredArgsConstructor
public enum IdempotentConsumeStatusEnum {

    /**
     * In progress.
     */
    CONSUMING("0"),

    /**
     * Consumed.
     */
    CONSUMED("1");

    @Getter
    private final String code;

    /**
     * Returns failure when the recorded state is still in progress.
     *
     * @param consumeStatus current consumption state
     * @return whether consumption should be treated as failed
     */
    public static boolean isError(String consumeStatus) {
        return Objects.equals(CONSUMING.code, consumeStatus);
    }
}
