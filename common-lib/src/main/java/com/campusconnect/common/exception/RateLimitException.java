package com.campusconnect.common.exception;

import com.campusconnect.common.web.ErrorCode;

/** Too many requests (default {@link ErrorCode#RATE_LIMITED}). */
public class RateLimitException extends BusinessException {

    public RateLimitException(String message) {
        super(ErrorCode.RATE_LIMITED, message);
    }

    public RateLimitException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
