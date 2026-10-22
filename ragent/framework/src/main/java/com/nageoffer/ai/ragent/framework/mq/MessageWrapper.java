package com.nageoffer.ai.ragent.framework.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Message body wrapper.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageWrapper<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Business key.
     */
    private String keys;

    /**
     * Business payload.
     */
    private T body;

    /**
     * Unique id used by the consumer for idempotency.
     */
    @Builder.Default
    private String uuid = UUID.randomUUID().toString();

    /**
     * Send time.
     */
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();
}
