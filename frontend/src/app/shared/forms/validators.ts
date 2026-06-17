import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * Reusable reactive-form validators (Story 9.7). These give precise, plain-language client-side feedback that
 * mirrors the server rules (they do not replace server enforcement). Empty is left to `Validators.required`.
 */

/** Valid datetime string that is strictly in the future. Errors: `{ datetime: true }` / `{ future: true }`. */
export function futureDateTime(control: AbstractControl): ValidationErrors | null {
  const raw = (control.value ?? '').toString().trim();
  if (!raw) {
    return null;
  }
  const t = Date.parse(raw);
  if (Number.isNaN(t)) {
    return { datetime: true };
  }
  return t > Date.now() ? null : { future: true };
}

/** A number strictly greater than zero. Errors: `{ number: true }` / `{ positive: true }`. */
export function positiveNumber(control: AbstractControl): ValidationErrors | null {
  const raw = (control.value ?? '').toString().trim();
  if (!raw) {
    return null;
  }
  const n = Number(raw);
  if (Number.isNaN(n)) {
    return { number: true };
  }
  return n > 0 ? null : { positive: true };
}
