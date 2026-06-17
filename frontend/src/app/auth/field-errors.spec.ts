import { describeControlError } from './field-errors';

describe('describeControlError', () => {
  it('returns null when there are no errors', () => {
    expect(describeControlError(null, 'Email')).toBeNull();
    expect(describeControlError({}, 'Email')).toBeNull();
  });

  it('maps common validators to plain language', () => {
    expect(describeControlError({ required: true }, 'Email')).toBe('Email is required.');
    expect(describeControlError({ email: true }, 'Email')).toContain('valid email');
    expect(describeControlError({ minlength: { requiredLength: 8, actualLength: 3 } }, 'Password')).toBe(
      'Password must be at least 8 characters.',
    );
    expect(describeControlError({ pattern: {} }, 'Code')).toBe('Enter a valid code.');
    expect(describeControlError({ mismatch: true }, 'Confirm password')).toBe('Passwords do not match.');
  });
});
