package com.campusconnect.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The error payload nested inside an error {@link ApiResponse}.
 *
 * <p>{@code code} is the machine-stable contract the frontend switches on (an {@link ErrorCode}
 * name). {@code message} is a human-readable hint and must never be parsed by clients.
 * {@code fields} carries per-field validation messages and is omitted when absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fields) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, Map<String, String> fields) {
        return new ApiError(code, message, fields);
    }
}
