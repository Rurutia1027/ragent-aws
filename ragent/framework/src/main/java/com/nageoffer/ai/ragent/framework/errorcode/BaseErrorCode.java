package com.nageoffer.ai.ragent.framework.errorcode;

/**
 * Standard error codes.
 *
 * <p>
 * Standard codes used across the system, following the A/B/C grouping:
 * <ul>
 *   <li>A: client errors</li>
 *   <li>B: service execution errors</li>
 *   <li>C: third-party service errors</li>
 * </ul>
 * Defined once in this module so services do not each invent the same codes.
 * </p>
 */
public enum BaseErrorCode implements IErrorCode {

    // ========== A: client errors ==========

    /**
     * Top-level client error.
     */
    CLIENT_ERROR("A000001", "Client error"),

    // ========== A01 registration errors ==========

    /**
     * Registration error.
     */
    USER_REGISTER_ERROR("A000100", "User registration error"),

    /**
     * Username validation failed.
     */
    USER_NAME_VERIFY_ERROR("A000110", "Username validation failed"),

    /**
     * Username already exists.
     */
    USER_NAME_EXIST_ERROR("A000111", "Username already exists"),

    /**
     * Username contains a sensitive word.
     */
    USER_NAME_SENSITIVE_ERROR("A000112", "Username contains a sensitive word"),

    /**
     * Username contains a special character.
     */
    USER_NAME_SPECIAL_CHARACTER_ERROR("A000113", "Username contains a special character"),

    /**
     * Password validation failed.
     */
    PASSWORD_VERIFY_ERROR("A000120", "Password validation failed"),

    /**
     * Password is too short.
     */
    PASSWORD_SHORT_ERROR("A000121", "Password is too short"),

    /**
     * Phone number format is invalid.
     */
    PHONE_VERIFY_ERROR("A000151", "Phone number format is invalid"),

    // ========== A02 idempotency errors ==========

    /**
     * Idempotency token is empty.
     */
    IDEMPOTENT_TOKEN_NULL_ERROR("A000200", "Idempotency token is empty"),

    /**
     * Idempotency token was already used or has expired.
     */
    IDEMPOTENT_TOKEN_DELETE_ERROR("A000201", "Idempotency token has already been used or has expired"),

    // ========== A03 query parameter errors ==========

    /**
     * Query size exceeds the maximum.
     */
    SEARCH_AMOUNT_EXCEEDS_LIMIT("A000300", "Query size exceeds the maximum"),

    // ========== B: service execution errors ==========

    /**
     * Top-level service execution error.
     */
    SERVICE_ERROR("B000001", "System execution error"),

    /**
     * Service execution timed out.
     */
    SERVICE_TIMEOUT_ERROR("B000100", "System execution timed out"),

    // ========== C: third-party service errors ==========

    /**
     * Top-level third-party service error.
     */
    REMOTE_ERROR("C000001", "Third-party service error");

    /**
     * Error code.
     */
    private final String code;

    /**
     * Error message.
     */
    private final String message;

    /**
     * Constructor.
     *
     * @param code    error code
     * @param message error message
     */
    BaseErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
