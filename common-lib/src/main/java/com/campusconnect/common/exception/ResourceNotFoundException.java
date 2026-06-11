package com.campusconnect.common.exception;

import com.campusconnect.common.web.ErrorCode;

/** A requested resource does not exist (default {@link ErrorCode#NOT_FOUND}). */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
