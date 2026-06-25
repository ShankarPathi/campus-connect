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
        top: calc(60px + var(--cc-space-3)); /* top-right corner, clear of the topbar */
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
      @keyframes toastIn {
        from {
          opacity: 0;
          transform: translateY(-10px) scale(0.98);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }
      .toast {
        display: flex;
        align-items: center;
        gap: var(--cc-space-2);
        min-width: 260px;
        max-width: 380px;
        padding: var(--cc-space-3) var(--cc-space-4);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-left-width: 4px;
        border-radius: var(--cc-radius-md);
        box-shadow: var(--cc-shadow-lg);
        font: var(--cc-text-body-medium);
        color: var(--cc-color-text);
        animation: toastIn 0.22s cubic-bezier(0.22, 1, 0.36, 1);
      }
      /* clearly green for success, red for failure — tinted background, not colour-alone */
      .toast--success {
        border-color: var(--cc-color-success);
        border-left-color: var(--cc-color-success);
        background: var(--cc-color-success-subtle, #ecfdf5);
        color: #065f46;
      }
      .toast--error {
        border-color: var(--cc-color-danger);
        border-left-color: var(--cc-color-danger);
        background: var(--cc-color-danger-subtle, #fef2f2);
        color: #991b1b;
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
