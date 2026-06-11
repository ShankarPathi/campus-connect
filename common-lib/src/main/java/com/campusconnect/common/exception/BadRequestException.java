package com.campusconnect.common.exception;

import com.campusconnect.common.web.ErrorCode;

import java.util.Map;

/** A business-rule or input failure (default {@link ErrorCode#BAD_REQUEST}). May carry field messages. */
public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BadRequestException(ErrorCode errorCode, String message, Map<String, String> fields) {
        super(errorCode, message, fields);
    }
}
