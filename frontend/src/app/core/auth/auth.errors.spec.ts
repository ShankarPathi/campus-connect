import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponseError } from '../http/api.models';
import { authErrorMessage, toAuthErrorView } from './auth.errors';

describe('authErrorMessage', () => {
  it('maps known codes to plain language and never leaks the code', () => {
    expect(authErrorMessage('INVALID_CREDENTIALS')).toBe('The email or password is incorrect.');
    expect(authErrorMessage('RATE_LIMITED')).toContain('Too many attempts');
    expect(authErrorMessage('OTP_EXPIRED')).toContain('expired');
  });

  it('falls back to a generic message for unknown/undefined codes (no code string)', () => {
    expect(authErrorMessage('SOME_NEW_CODE')).toBe('Something went wrong — please try again.');
    expect(authErrorMessage(undefined)).toBe('Something went wrong — please try again.');
    expect(authErrorMessage('SOME_NEW_CODE')).not.toContain('SOME_NEW_CODE');
  });
});

describe('toAuthErrorView', () => {
  it('spreads VALIDATION_ERROR.fields inline with no form banner', () => {
    const err = new ApiResponseError({
      code: 'VALIDATION_ERROR',
      message: 'Validation failed',
      fields: { email: 'must be a well-formed email address', password: 'size must be between 8 and 72' },
    });
    const view = toAuthErrorView(err);
    expect(view.formMessage).toBeNull();
    expect(view.fieldErrors['email']).toContain('email');
    expect(view.fieldErrors['password']).toContain('8');
  });

  it('targets EMAIL_ALREADY_EXISTS at the email field', () => {
    const view = toAuthErrorView(new ApiResponseError({ code: 'EMAIL_ALREADY_EXISTS', message: 'x' }));
    expect(view.formMessage).toBeNull();
    expect(view.fieldErrors['email']).toBe('An account with this email already exists.');
  });

  it('targets OTP_INVALID at the otp field', () => {
    const view = toAuthErrorView(new ApiResponseError({ code: 'OTP_INVALID', message: 'x' }));
    expect(view.fieldErrors['otp']).toBeDefined();
  });

  it('puts business codes in the form banner', () => {
    const view = toAuthErrorView(new ApiResponseError({ code: 'INVALID_CREDENTIALS', message: 'x' }));
    expect(view.formMessage).toBe('The email or password is incorrect.');
    expect(view.fieldErrors).toEqual({});
  });

  it('reads the envelope out of a raw 401 HttpErrorResponse (the login INVALID_CREDENTIALS path)', () => {
    // The auth interceptor rethrows a 401 on auth endpoints untouched, so login gets the raw HttpErrorResponse.
    const httpErr = new HttpErrorResponse({
      status: 401,
      error: { success: false, error: { code: 'INVALID_CREDENTIALS', message: 'bad' } },
    });
    const view = toAuthErrorView(httpErr);
    expect(view.formMessage).toBe('The email or password is incorrect.');
  });

  it('maps an unrecognizable error (network failure) to the generic banner', () => {
    const view = toAuthErrorView(new Error('Http failure'));
    expect(view.formMessage).toBe('Something went wrong — please try again.');
  });
});
