package com.campusconnect.common.exception;

import com.campusconnect.common.web.ErrorCode;

/** A uniqueness/state conflict (default {@link ErrorCode#CONFLICT}). */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public DuplicateResourceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
