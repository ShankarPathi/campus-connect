import { Component, computed, input } from '@angular/core';
import { StatusVariant } from '../ui.models';

/** Per-variant glyph so status reads by icon + label + color, never color alone (UX-DR10, Story 9.7). */
const GLYPH: Record<StatusVariant, string> = {
  success: '✓',
  warning: '!',
  danger: '✕',
  info: 'i',
  neutral: '•',
};

/**
 * Status pill (Story 9.1) — caption type, full radius, status-color text on its `_subtle` background.
 * Variant-driven (dumb): callers map a backend enum to a variant via `statusToVariant` and pass a human `label`.
 * The leading glyph is decorative (`aria-hidden`) — the label already carries the meaning for assistive tech.
 */
@Component({
  selector: 'app-status-pill',
  standalone: true,
  template: `<span class="pill" [class]="'pill--' + variant()"><span class="pill__glyph" aria-hidden="true">{{ glyph() }}</span>{{ label() }}</span>`,
  styles: [
    `
      .pill {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        border-radius: var(--cc-radius-full);
        padding: 2px 10px;
        font: var(--cc-text-caption);
        letter-spacing: 0.05em;
        text-transform: uppercase;
        white-space: nowrap;
      }
      .pill__glyph {
        font-weight: 700;
        line-height: 1;
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
  readonly glyph = computed(() => GLYPH[this.variant()]);
}
