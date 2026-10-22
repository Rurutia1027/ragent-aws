package com.nageoffer.ai.ragent.framework.errorcode;

/**
 * Platform error code.
 * Implemented by each error-code type.
 */
public interface IErrorCode {

    /**
     * Error code.
     */
    String code();

    /**
     * Error message.
     */
    String message();
}
