import { ValidationErrors } from '@angular/forms';

/**
 * Map Angular reactive-form validator errors to plain-language copy (Story 9.3, UX-DR12). Shared by the
 * auth screens so client-side messages read the same everywhere — specific, recoverable, never a code.
 */
export function describeControlError(errors: ValidationErrors | null | undefined, label: string): string | null {
  if (!errors || Object.keys(errors).length === 0) {
    return null;
  }
  if (errors['required']) {
    return `${label} is required.`;
  }
  if (errors['email']) {
    return 'Enter a valid email address.';
  }
  if (errors['minlength']) {
    return `${label} must be at least ${errors['minlength'].requiredLength} characters.`;
  }
  if (errors['maxlength']) {
    return `${label} must be at most ${errors['maxlength'].requiredLength} characters.`;
  }
  if (errors['pattern']) {
    return `Enter a valid ${label.toLowerCase()}.`;
  }
  if (errors['mismatch']) {
    return 'Passwords do not match.';
  }
  return `Check ${label.toLowerCase()}.`;
}
