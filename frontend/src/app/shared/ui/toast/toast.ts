import { Component, computed, inject } from '@angular/core';
import { ToastService } from './toast.service';

/**
 * Toast host (Story 9.4; a11y hardening Story 9.7) — renders the `ToastService` queue in a fixed stack,
 * announced via ARIA live regions. Success/info go to a **polite** region; errors go to an **assertive**
 * `role="alert"` region so they interrupt and are read promptly. Mounted once in the app shell.
 * Token-styled; never color-alone (an icon + text).
 */
@Component({
  selector: 'app-toast',
  standalone: true,
  template: `
    <div class="toasts">
      <div class="region" role="status" aria-live="polite" aria-atomic="false">
        @for (t of politeToasts(); track t.id) {
          <div class="toast" [class]="'toast--' + t.variant">
            <span class="toast__icon" aria-hidden="true">{{ t.variant === 'success' ? '✓' : '!' }}</span>
            <span class="toast__text">{{ t.text }}</span>
            <button class="toast__close" type="button" aria-label="Dismiss" (click)="toast.dismiss(t.id)">×</button>
          </div>
        }
      </div>
      <div class="region" role="alert" aria-live="assertive" aria-atomic="false">
        @for (t of errorToasts(); track t.id) {
          <div class="toast toast--error">
            <span class="toast__icon" aria-hidden="true">!</span>
            <span class="toast__text">{{ t.text }}</span>
            <button class="toast__close" type="button" aria-label="Dismiss" (click)="toast.dismiss(t.id)">×</button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .toasts {
        position: fixed;
        bottom: var(--cc-space-6);
        right: var(--cc-space-6);
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-2);
        z-index: 1000;
      }
      .region {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-2);
      }
      .toast {
        display: flex;
        align-items: center;
        gap: var(--cc-space-2);
        min-width: 240px;
        max-width: 360px;
        padding: var(--cc-space-3) var(--cc-space-4);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-left-width: 3px;
        border-radius: var(--cc-radius-md);
        box-shadow: var(--cc-shadow-lg);
        font: var(--cc-text-body);
        color: var(--cc-color-text);
      }
      .toast--success {
        border-left-color: var(--cc-color-success);
      }
      .toast--error {
        border-left-color: var(--cc-color-danger);
      }
      .toast__icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 18px;
        height: 18px;
        border-radius: var(--cc-radius-full);
        font: var(--cc-text-caption);
        color: var(--cc-color-text-inverse);
      }
      .toast--success .toast__icon {
        background: var(--cc-color-success);
      }
      .toast--error .toast__icon {
        background: var(--cc-color-danger);
      }
      .toast__text {
        flex: 1;
      }
      .toast__close {
        border: none;
        background: transparent;
        cursor: pointer;
        font-size: 18px;
        line-height: 1;
        color: var(--cc-color-text-muted);
      }
    `,
  ],
})
export class Toast {
  protected readonly toast = inject(ToastService);
  protected readonly politeToasts = computed(() => this.toast.toasts().filter((t) => t.variant !== 'error'));
  protected readonly errorToasts = computed(() => this.toast.toasts().filter((t) => t.variant === 'error'));
}
