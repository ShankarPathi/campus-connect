package com.campusconnect.common.exception;

import com.campusconnect.common.web.ErrorCode;

/** Authentication failure (default {@link ErrorCode#UNAUTHORIZED}). */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
