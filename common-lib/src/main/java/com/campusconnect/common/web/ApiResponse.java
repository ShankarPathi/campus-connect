package com.campusconnect.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single response envelope every endpoint returns.
 *
 * <p>Success: {@code { "success": true, "data": ..., "message": ... }} (no {@code error}).<br>
 * Error: {@code { "success": false, "error": { "code", "message", "fields" } }} (no {@code data}).
 *
 * <p>{@code @JsonInclude(NON_NULL)} is what makes a success body omit {@code error} and an error
 * body omit {@code data}/{@code message} — keeping the wire shape exactly as specified.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, null, error);
    }
}
