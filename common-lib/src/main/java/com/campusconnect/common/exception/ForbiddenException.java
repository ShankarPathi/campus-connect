package com.campusconnect.common.exception;

import com.campusconnect.common.web.ErrorCode;

/** Authorization/status failure — authenticated but not permitted (default {@link ErrorCode#FORBIDDEN}). */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
