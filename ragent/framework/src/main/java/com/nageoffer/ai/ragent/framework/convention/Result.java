package com.nageoffer.ai.ragent.framework.convention;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * Uniform API response.
 *
 * <p>
 * One response shape for every API, so clients do not have to handle a different envelope per endpoint.
 * Wrap every response in this type.
 * </p>
 *
 * @param <T> response payload type
 */
@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 5679018624309023727L;

    /**
     * Success code.
     * <p>
     * Returned when the request succeeds.
     * </p>
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * Status code.
     * <p>
     * Processing result. {@code "0"} means success. Any other value is an error.
     * </p>
     */
    private String code;

    /**
     * Response message.
     * <p>
     * Human-readable result. A success note, or the reason for a failure.
     * </p>
     */
    private String message;

    /**
     * Response payload.
     * <p>
     * Business payload. The type is {@code T}. May be {@code null} when the request fails.
     * </p>
     */
    private T data;

    /**
     * Request trace id.
     * <p>
     * Unique id for tracing and debugging.
     * </p>
     */
    private String requestId;

    /**
     * Whether the request succeeded.
     *
     * @return {@code true} when the code is {@link #SUCCESS_CODE}
     */
    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
