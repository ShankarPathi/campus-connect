import { Component, input } from '@angular/core';
import { EligibilityCheck } from '../ui.models';

/**
 * Eligibility panel (Story 9.1) — the product's signature component. A bordered list of criteria checks; each
 * row shows ✓ (success) / ✕ (danger), the rule label, and the comparison detail. Failing rows sit on
 * danger_subtle. The reason (`detail`) is ALWAYS shown — never a bare "not eligible".
 */
@Component({
  selector: 'app-eligibility-panel',
  standalone: true,
  template: `
    <ul class="panel">
      @for (check of checks(); track $index) {
        <li class="row" [class.row--fail]="!check.passed">
          <span
            class="icon"
            aria-hidden="true"
            [class.icon--pass]="check.passed"
            [class.icon--fail]="!check.passed"
            >{{ check.passed ? '✓' : '✕' }}</span
          >
          <span class="cc-sr-only">{{ check.passed ? 'Passed:' : 'Failed:' }}</span>
          <span class="label">{{ check.label }}</span>
          <span class="detail">{{ check.detail }}</span>
        </li>
      }
    </ul>
  `,
  styles: [
    `
      .panel {
        list-style: none;
        margin: 0;
        padding: 0;
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        background: var(--cc-color-surface-raised);
        overflow: hidden;
      }
      .row {
        display: grid;
        grid-template-columns: 20px 1fr auto;
        align-items: center;
        gap: var(--cc-space-3);
        padding: var(--cc-space-3) var(--cc-space-4);
        font: var(--cc-text-body);
        color: var(--cc-color-text);
      }
      .row + .row {
        border-top: 1px solid var(--cc-color-border);
      }
      .row--fail {
        background: var(--cc-color-danger-subtle);
        color: var(--cc-color-danger);
      }
      .icon {
        display: inline-flex;
        justify-content: center;
        font: var(--cc-text-body-medium);
      }
      .icon--pass {
        color: var(--cc-color-success);
      }
      .icon--fail {
        color: var(--cc-color-danger);
      }
      .detail {
        font: var(--cc-text-small);
        color: var(--cc-color-text-secondary);
      }
      .row--fail .detail {
        color: var(--cc-color-danger);
      }
    `,
  ],
})
export class EligibilityPanel {
  readonly checks = input<EligibilityCheck[]>([]);
}
