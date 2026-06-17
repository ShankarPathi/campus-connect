import { HttpErrorResponse } from '@angular/common/http';
import { ApiError, ApiResponse, ApiResponseError } from '../http/api.models';

/**
 * Plain-language auth error mapping (Story 9.3, UX-DR12). Translates the backend `ErrorCode` enum into
 * human copy — the UI must NEVER show a raw code or stack trace. Mirrors common-lib/web/ErrorCode.java.
 */
const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'The email or password is incorrect.',
  EMAIL_NOT_VERIFIED: 'Please verify your email first — check your inbox.',
  RECRUITER_NOT_APPROVED: 'Your account is awaiting College Admin approval.',
  ACCOUNT_INACTIVE: "This account isn't active. Contact your College Admin.",
  EMAIL_ALREADY_EXISTS: 'An account with this email already exists.',
  EMAIL_VERIFY_TOKEN_INVALID: 'This verification link is invalid or has already been used.',
  OTP_INVALID: "That code isn't right — check your email.",
  OTP_EXPIRED: 'That code has expired — request a new one.',
  RATE_LIMITED: 'Too many attempts — please wait a few minutes and try again.',
};

const GENERIC = 'Something went wrong — please try again.';

/** Map a backend error code to plain copy (never the code itself). Unknown codes fall back to a generic line. */
export function authErrorMessage(code: string | undefined): string {
  return (code && MESSAGES[code]) || GENERIC;
}

/** Codes that are best shown inline against a specific field rather than as a form-level banner. */
const FIELD_TARGETED: Record<string, string> = {
  EMAIL_ALREADY_EXISTS: 'email',
  OTP_INVALID: 'otp',
};

export interface AuthErrorView {
  /** A form-level banner message, or null when every part of the error landed on a field. */
  formMessage: string | null;
  /** Field name → inline message (keyed by the DTO field, e.g. `email`, `otp`, `password`). */
  fieldErrors: Record<string, string>;
}

/**
 * Normalize a thrown error to its `{code, message, fields}` envelope. The auth interceptor rethrows a
 * **401 untouched** on the per-portal auth endpoints (it can't refresh a login), so `INVALID_CREDENTIALS`
 * arrives as a raw {@link HttpErrorResponse} carrying the envelope — not a typed {@link ApiResponseError}.
 * Handle both shapes so the login screen still shows plain copy.
 */
function extractApiError(err: unknown): ApiError | null {
  if (err instanceof ApiResponseError) {
    return { code: err.code, message: err.message, fields: err.fields };
  }
  if (err instanceof HttpErrorResponse) {
    const envelope = err.error as ApiResponse<unknown> | null;
    if (envelope && typeof envelope === 'object' && envelope.success === false && envelope.error) {
      return envelope.error;
    }
  }
  return null;
}

/**
 * Split any thrown error into a form-level message + per-field inline errors. `VALIDATION_ERROR` spreads
 * its `fields` map inline; a couple of business codes are field-targeted; everything else is form-level.
 * An unrecognizable error (e.g. a raw network failure) becomes the generic form message.
 */
export function toAuthErrorView(err: unknown): AuthErrorView {
  const apiError = extractApiError(err);
  if (!apiError) {
    return { formMessage: GENERIC, fieldErrors: {} };
  }
  if (apiError.code === 'VALIDATION_ERROR' && apiError.fields) {
    return { formMessage: null, fieldErrors: { ...apiError.fields } };
  }
  const field = FIELD_TARGETED[apiError.code];
  if (field) {
    return { formMessage: null, fieldErrors: { [field]: authErrorMessage(apiError.code) } };
  }
  return { formMessage: authErrorMessage(apiError.code), fieldErrors: {} };
}
