package com.campusconnect.common.web;

import org.springframework.http.HttpStatus;

/**
 * The closed set of error codes. The enum <b>constant name is the wire {@code code}</b> the
 * frontend switches on, and each constant carries its own {@link HttpStatus} so the
 * {@link GlobalExceptionHandler} derives the response status without branching.
 *
 * <p>Every code maps only to the architecture's allowed HTTP set (400/401/403/404/409/429/500).
 * Adding a feature code later is a one-line addition here. Keep code names stable once shipped —
 * renaming a code is a breaking API change.
 */
public enum ErrorCode {

    // ── generic baseline ──
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(HttpStatus.CONFLICT),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // ── tenancy / platform (Epic 1) ──
    TENANT_SLUG_TAKEN(HttpStatus.CONFLICT),

    // ── auth (Epic 2) ──
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    EMAIL_VERIFY_TOKEN_INVALID(HttpStatus.BAD_REQUEST),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN),
    RECRUITER_NOT_APPROVED(HttpStatus.FORBIDDEN),
    OTP_INVALID(HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),

    // ── profile (Epic 3) ──
    PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST),
    PROFILE_NOT_APPROVED(HttpStatus.FORBIDDEN),
    PROFILE_LOCKED(HttpStatus.CONFLICT),
    RESUME_INVALID_TYPE(HttpStatus.BAD_REQUEST),
    RESUME_TOO_LARGE(HttpStatus.BAD_REQUEST),

    // ── drive (Epic 4) ──
    DRIVE_NOT_OPEN(HttpStatus.CONFLICT),
    DRIVE_DEADLINE_PASSED(HttpStatus.CONFLICT),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT),

    // ── eligibility / apply (Epic 5) ──
    NOT_ELIGIBLE(HttpStatus.BAD_REQUEST),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT),
    WITHDRAW_NOT_ALLOWED(HttpStatus.CONFLICT),

    // ── offer / placement (Epic 7) ──
    STUDENT_NOT_SELECTED(HttpStatus.CONFLICT),
    OFFER_EXPIRED(HttpStatus.CONFLICT),
    OFFER_ALREADY_RESPONDED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /** The HTTP status this code maps to. */
    public HttpStatus status() {
        return status;
    }

    /**
     * Maps an HTTP status to its generic {@link ErrorCode} — used when wrapping Spring's built-in
     * MVC exceptions (malformed body, wrong method, etc.) into the standard envelope. Any
     * unrecognised status falls back to {@link #INTERNAL_ERROR}.
     */
    public static ErrorCode fromStatus(org.springframework.http.HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMITED;
            default -> INTERNAL_ERROR;
        };
    }
}
