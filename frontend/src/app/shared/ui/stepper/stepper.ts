import { Component, input } from '@angular/core';
import { StepItem } from '../ui.models';

/**
 * Horizontal node stepper (Story 9.1) — the multi-round interview track + application timeline.
 * done = success, current = primary, upcoming = muted; connector lines between nodes.
 */
@Component({
  selector: 'app-stepper',
  standalone: true,
  template: `
    <ol class="stepper">
      @for (step of steps(); track $index) {
        <li class="step" [class]="'step--' + step.state">
          @if (!$first) {
            <span class="connector"></span>
          }
          <span class="node">{{ step.state === 'done' ? '✓' : $index + 1 }}</span>
          <span class="label">{{ step.label }}</span>
        </li>
      }
    </ol>
  `,
  styles: [
    `
      .stepper {
        display: flex;
        list-style: none;
        margin: 0;
        padding: 0;
      }
      .step {
        position: relative;
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--cc-space-2);
        text-align: center;
      }
      .connector {
        position: absolute;
        top: 14px;
        right: 50%;
        width: 100%;
        height: 2px;
        background: var(--cc-color-border);
      }
      .node {
        position: relative;
        z-index: 1;
        width: 28px;
        height: 28px;
        border-radius: var(--cc-radius-full);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font: var(--cc-text-caption);
        border: 2px solid var(--cc-color-border-strong);
        background: var(--cc-color-surface-raised);
        color: var(--cc-color-text-muted);
      }
      .label {
        font: var(--cc-text-small);
        color: var(--cc-color-text-secondary);
      }
      .step--done .node {
        border-color: var(--cc-color-success);
        background: var(--cc-color-success);
        color: var(--cc-color-surface-raised);
      }
      .step--done .connector {
        background: var(--cc-color-success);
      }
      .step--current .node {
        border-color: var(--cc-color-primary);
        color: var(--cc-color-primary);
        background: var(--cc-color-primary-subtle);
      }
      .step--current .label {
        color: var(--cc-color-text);
        font: var(--cc-text-body-medium);
      }
    `,
  ],
})
export class Stepper {
  readonly steps = input<StepItem[]>([]);
}
