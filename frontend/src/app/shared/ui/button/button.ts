import { Component, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md';

/**
 * Button (Story 9.3) — the shared action primitive (DESIGN.md: indigo solid primary, outline secondary,
 * indigo-text ghost, solid danger). Renders a real `<button>` so native click + form submit work; the
 * `loading` state disables it and shows an inline spinner. Token-styled — no hard-coded hex.
 */
@Component({
  selector: 'app-button',
  standalone: true,
  template: `
    <button
      class="btn"
      [class]="'btn--' + variant() + ' btn--' + size()"
      [attr.type]="type()"
      [disabled]="disabled() || loading()"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      @if (loading()) {
        <span class="btn__spinner" aria-hidden="true"></span>
      }
      <span class="btn__label"><ng-content /></span>
    </button>
  `,
  styles: [
    `
      :host {
        display: inline-block;
      }
      .btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: var(--cc-space-2);
        font: var(--cc-text-body-medium);
        font-weight: 600;
        border-radius: var(--cc-radius-md);
        border: 1px solid transparent;
        cursor: pointer;
        white-space: nowrap;
        transition:
          background-color 0.12s ease,
          border-color 0.12s ease,
          box-shadow 0.14s ease,
          transform 0.14s ease;
      }
      .btn--primary,
      .btn--danger {
        box-shadow: var(--cc-shadow-sm);
      }
      .btn--primary:hover:not(:disabled),
      .btn--danger:hover:not(:disabled) {
        box-shadow: var(--cc-shadow-md);
        transform: translateY(-1px);
      }
      .btn:active:not(:disabled) {
        transform: translateY(0);
      }
      .btn:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .btn:focus-visible {
        outline: 2px solid var(--cc-color-primary);
        outline-offset: 2px;
      }
      .btn--sm {
        padding: var(--cc-space-1) var(--cc-space-3);
        min-height: 32px;
      }
      .btn--md {
        padding: var(--cc-space-2) var(--cc-space-4);
        min-height: 40px;
      }
      .btn--primary {
        background: var(--cc-color-primary);
        color: var(--cc-color-text-inverse);
      }
      .btn--primary:hover:not(:disabled) {
        background: var(--cc-color-primary-hover);
      }
      .btn--secondary {
        background: var(--cc-color-surface-raised);
        color: var(--cc-color-text);
        border-color: var(--cc-color-border-strong);
      }
      .btn--secondary:hover:not(:disabled) {
        background: var(--cc-color-surface);
      }
      .btn--ghost {
        background: transparent;
        color: var(--cc-color-primary);
      }
      .btn--ghost:hover:not(:disabled) {
        background: var(--cc-color-primary-subtle);
      }
      .btn--danger {
        background: var(--cc-color-danger);
        color: var(--cc-color-text-inverse);
      }
      .btn--danger:hover:not(:disabled) {
        filter: brightness(0.94);
      }
      .btn__spinner {
        width: 14px;
        height: 14px;
        border: 2px solid currentColor;
        border-right-color: transparent;
        border-radius: var(--cc-radius-full);
        animation: btn-spin 0.6s linear infinite;
      }
      @keyframes btn-spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);
}
