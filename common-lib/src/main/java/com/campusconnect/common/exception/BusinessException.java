package com.campusconnect.common.exception;

import com.campusconnect.common.web.ErrorCode;

import java.util.Map;

/**
 * Base type for all expected, mapped failures. Carries an {@link ErrorCode} (which determines both
 * the wire {@code code} and the HTTP status) and optional per-field messages. The
 * {@code GlobalExceptionHandler} translates this into the standard error envelope.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, String> fields;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, String> fields) {
        super(message);
        this.errorCode = errorCode;
        this.fields = fields;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
