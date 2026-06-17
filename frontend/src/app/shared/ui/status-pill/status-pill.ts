import { Component, input } from '@angular/core';
import { StatusVariant } from '../ui.models';

/**
 * Status pill (Story 9.1) — caption type, full radius, status-color text on its `_subtle` background.
 * Variant-driven (dumb): callers map a backend enum to a variant via `statusToVariant` and pass a human `label`.
 */
@Component({
  selector: 'app-status-pill',
  standalone: true,
  template: `<span class="pill" [class]="'pill--' + variant()">{{ label() }}</span>`,
  styles: [
    `
      .pill {
        display: inline-flex;
        align-items: center;
        border-radius: var(--cc-radius-full);
        padding: 2px 10px;
        font: var(--cc-text-caption);
        letter-spacing: 0.05em;
        text-transform: uppercase;
        white-space: nowrap;
      }
      .pill--success {
        color: var(--cc-color-success);
        background: var(--cc-color-success-subtle);
      }
      .pill--warning {
        color: var(--cc-color-warning);
        background: var(--cc-color-warning-subtle);
      }
      .pill--danger {
        color: var(--cc-color-danger);
        background: var(--cc-color-danger-subtle);
      }
      .pill--info {
        color: var(--cc-color-info);
        background: var(--cc-color-info-subtle);
      }
      .pill--neutral {
        color: var(--cc-color-text-secondary);
        background: var(--cc-color-surface);
      }
    `,
  ],
})
export class StatusPill {
  readonly label = input('');
  readonly variant = input<StatusVariant>('neutral');
}
