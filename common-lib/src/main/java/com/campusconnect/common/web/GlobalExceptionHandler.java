package com.campusconnect.common.web;

import com.campusconnect.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single advice that maps every exception to the standard {@link ApiResponse} error envelope.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so that Spring's own MVC exceptions — malformed
 * body, wrong HTTP method, unsupported media type, missing/mistyped params, unmatched routes,
 * method-level validation — are funnelled through {@link #handleExceptionInternal} and wrapped in
 * our envelope with the correct 4xx status, instead of collapsing to a generic 500.
 *
 * <p>Lives in {@code com.campusconnect.common.web}; every service component-scans
 * {@code com.campusconnect}, so this applies globally with no per-service wiring. The catch-all
 * returns a generic message and logs the real cause server-side — a stack trace or exception class
 * is never sent to the client (NFR-2).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Mapped business/domain failures — status and code come from the {@link ErrorCode}. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex) {
        // Defensive: a null ErrorCode must not NPE out of the advice (would escape the envelope).
        ErrorCode code = ex.getErrorCode() != null ? ex.getErrorCode() : ErrorCode.INTERNAL_ERROR;
        if (code == ErrorCode.INTERNAL_ERROR && ex.getErrorCode() == null) {
            log.error("BusinessException raised without an ErrorCode", ex);
        }
        ApiError error = new ApiError(code.name(), ex.getMessage(), ex.getFields());
        return ResponseEntity.status(code.status()).body(ApiResponse.error(error));
    }

    /**
     * Constraint violations from {@code @Validated} beans/params → 400 with per-field messages.
     * Keys use the leaf property name (not the internal {@code method.arg0.field} path).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> fields.putIfAbsent(leafName(v.getPropertyPath().toString()), v.getMessage()));
        ApiError error = new ApiError(ErrorCode.VALIDATION_ERROR.name(), "Validation failed", fields);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(ApiResponse.error(error));
    }

    /**
     * Authorization denials thrown by method security ({@code @PreAuthorize}) reach the advice (not the
     * filter chain's access-denied handler), so map them to a 403 envelope here. Without this, the
     * catch-all below would turn them into 500s.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        ApiError error = ApiError.of(ErrorCode.FORBIDDEN.name(), "Access denied");
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(ApiResponse.error(error));
    }

    /**
     * A unique-index violation (e.g. a TOCTOU race past an existence pre-check) → 409. Generic
     * message — the DB error detail is never returned.
     */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<Object> handleDuplicateKey(org.springframework.dao.DuplicateKeyException ex) {
        log.warn("Unique constraint violation", ex);
        ApiError error = ApiError.of(ErrorCode.CONFLICT.name(), "A resource with the same unique key already exists");
        return ResponseEntity.status(ErrorCode.CONFLICT.status()).body(ApiResponse.error(error));
    }

    /** Anything unmapped (non-Spring, non-business) → 500 generic; real cause logged, never returned. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiError error = ApiError.of(ErrorCode.INTERNAL_ERROR.name(), "An unexpected error occurred");
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(ApiResponse.error(error));
    }

    /**
     * The single funnel for every Spring MVC built-in exception (invoked by the parent's handlers).
     * Wraps the resolved status in the {@link ApiResponse} envelope. Bean-validation failures get a
     * {@code VALIDATION_ERROR} code (with field messages where available); everything else gets the
     * generic code for its status with a safe, non-leaking message.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Spring MVC exception", ex);
        }

        ApiError error;
        HttpStatusCode status = statusCode;

        if (ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException) {
            // Multipart upload past the servlet hard ceiling (Story 3.2) → 400 RESUME_TOO_LARGE, matching
            // the service-level size check so an oversized file is never a raw 500.
            error = ApiError.of(ErrorCode.RESUME_TOO_LARGE.name(), "The uploaded file is too large");
            status = ErrorCode.RESUME_TOO_LARGE.status();
        } else if (ex instanceof MethodArgumentNotValidException manve) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (FieldError fe : manve.getBindingResult().getFieldErrors()) {
                fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
            }
            error = new ApiError(ErrorCode.VALIDATION_ERROR.name(), "Validation failed", fields);
            status = ErrorCode.VALIDATION_ERROR.status();
        } else if (statusCode.value() == HttpStatus.BAD_REQUEST.value()
                && ex.getClass().getSimpleName().equals("HandlerMethodValidationException")) {
            // Method-level @Valid params (Spring 6.1+/Boot 4) — keep it 400 VALIDATION_ERROR.
            error = ApiError.of(ErrorCode.VALIDATION_ERROR.name(), "Validation failed");
            status = ErrorCode.VALIDATION_ERROR.status();
        } else {
            ErrorCode code = ErrorCode.fromStatus(statusCode);
            error = ApiError.of(code.name(), genericMessageFor(statusCode));
        }

        return new ResponseEntity<>(ApiResponse.error(error), headers, status);
    }

    /** A safe, generic message derived from the status reason phrase — never echoes exception detail. */
    private static String genericMessageFor(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Request failed";
    }

    /** Returns the last node of a constraint property path (e.g. {@code save.arg0.email} → {@code email}). */
    private static String leafName(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }
}
