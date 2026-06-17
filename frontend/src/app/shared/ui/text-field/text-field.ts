import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let uid = 0;

/**
 * TextField (Story 9.3) — a labelled input implementing ControlValueAccessor, so it drops into reactive
 * forms via `formControlName`. Renders an inline error in danger red and wires `aria-invalid` +
 * `aria-describedby` (error/hint) for accessibility. The label `for` ties to a unique input id.
 * Token-styled — no hard-coded hex.
 */
@Component({
  selector: 'app-text-field',
  standalone: true,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextField), multi: true },
  ],
  template: `
    <div class="field">
      <label class="field__label" [attr.for]="id">
        {{ label() }}@if (required()) {<span class="field__req" aria-hidden="true"> *</span>}
      </label>
      <input
        class="field__input"
        [class.field__input--error]="!!error()"
        [id]="id"
        [type]="type()"
        [attr.autocomplete]="autocomplete()"
        [attr.inputmode]="inputmode()"
        [attr.placeholder]="placeholder() || null"
        [attr.aria-invalid]="error() ? 'true' : null"
        [attr.aria-describedby]="describedBy()"
        [attr.required]="required() ? '' : null"
        [value]="value()"
        [disabled]="disabled()"
        (input)="onInput($event)"
        (blur)="onTouched()"
      />
      @if (error()) {
        <p class="field__error" [id]="id + '-err'">{{ error() }}</p>
      } @else if (hint()) {
        <p class="field__hint" [id]="id + '-hint'">{{ hint() }}</p>
      }
    </div>
  `,
  styles: [
    `
      .field {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-1);
      }
      .field__label {
        font: var(--cc-text-body-medium);
        color: var(--cc-color-text);
      }
      .field__req {
        color: var(--cc-color-danger);
      }
      .field__input {
        font: var(--cc-text-body);
        color: var(--cc-color-text);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border-strong);
        border-radius: var(--cc-radius-sm);
        padding: var(--cc-space-2) var(--cc-space-3);
        min-height: 40px;
      }
      .field__input::placeholder {
        color: var(--cc-color-text-muted);
      }
      .field__input:focus-visible {
        outline: 2px solid var(--cc-color-primary);
        outline-offset: 1px;
        border-color: var(--cc-color-primary);
      }
      .field__input:disabled {
        background: var(--cc-color-surface);
        opacity: 0.7;
      }
      .field__input--error {
        border-color: var(--cc-color-danger);
      }
      .field__error {
        font: var(--cc-text-small);
        color: var(--cc-color-danger);
        margin: 0;
      }
      .field__hint {
        font: var(--cc-text-small);
        color: var(--cc-color-text-secondary);
        margin: 0;
      }
    `,
  ],
})
export class TextField implements ControlValueAccessor {
  readonly id = `cc-tf-${uid++}`;

  readonly label = input('');
  readonly type = input<'text' | 'email' | 'password' | 'tel'>('text');
  readonly autocomplete = input<string | null>(null);
  readonly inputmode = input<string | null>(null);
  readonly placeholder = input('');
  readonly hint = input('');
  readonly error = input('');
  readonly required = input(false);

  readonly value = signal('');
  readonly disabled = signal(false);

  private onChange: (v: string) => void = () => {};
  protected onTouched: () => void = () => {};

  protected describedBy(): string | null {
    if (this.error()) {
      return `${this.id}-err`;
    }
    if (this.hint()) {
      return `${this.id}-hint`;
    }
    return null;
  }

  protected onInput(event: Event): void {
    const v = (event.target as HTMLInputElement).value;
    this.value.set(v);
    this.onChange(v);
  }

  writeValue(value: string): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (v: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}
