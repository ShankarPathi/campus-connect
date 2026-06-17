import { Component, computed, input, model } from '@angular/core';
import { Portal } from '../../core/auth/auth.models';

interface PortalOption {
  value: Portal;
  label: string;
}

const ALL: PortalOption[] = [
  { value: 'student', label: 'Student' },
  { value: 'recruiter', label: 'Recruiter' },
  { value: 'admin', label: 'Admin' },
];

/**
 * PortalToggle (Story 9.3) — a segmented control that selects which portal's `/api/<portal>/auth/*`
 * endpoint the auth screens hit. A WAI-ARIA radiogroup: roving arrow-key selection, one tab stop.
 * `allowed` narrows the options (register passes student/recruiter only — admins can't self-register).
 */
@Component({
  selector: 'app-portal-toggle',
  standalone: true,
  template: `
    <div class="seg" role="radiogroup" [attr.aria-label]="ariaLabel()">
      @for (opt of options(); track opt.value) {
        <button
          type="button"
          class="seg__opt"
          role="radio"
          [class.seg__opt--on]="value() === opt.value"
          [attr.aria-checked]="value() === opt.value"
          [attr.tabindex]="value() === opt.value ? 0 : -1"
          (click)="select(opt.value)"
          (keydown)="onKey($event)"
        >
          {{ opt.label }}
        </button>
      }
    </div>
  `,
  styles: [
    `
      .seg {
        display: inline-flex;
        width: 100%;
        gap: var(--cc-space-1);
        padding: var(--cc-space-1);
        background: var(--cc-color-surface);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-md);
      }
      .seg__opt {
        flex: 1;
        font: var(--cc-text-body-medium);
        color: var(--cc-color-text-secondary);
        background: transparent;
        border: none;
        border-radius: var(--cc-radius-sm);
        padding: var(--cc-space-2) var(--cc-space-3);
        cursor: pointer;
        min-height: 36px;
      }
      .seg__opt--on {
        background: var(--cc-color-surface-raised);
        color: var(--cc-color-primary);
        box-shadow: var(--cc-shadow-sm);
      }
      .seg__opt:focus-visible {
        outline: 2px solid var(--cc-color-primary);
        outline-offset: 1px;
      }
    `,
  ],
})
export class PortalToggle {
  readonly value = model<Portal>('student');
  readonly allowed = input<Portal[]>(['student', 'recruiter', 'admin']);
  readonly ariaLabel = input('Choose your portal');

  readonly options = computed(() => ALL.filter((o) => this.allowed().includes(o.value)));

  protected select(p: Portal): void {
    this.value.set(p);
  }

  protected onKey(event: KeyboardEvent): void {
    const opts = this.options();
    const i = opts.findIndex((o) => o.value === this.value());
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.value.set(opts[(i + 1) % opts.length].value);
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.value.set(opts[(i - 1 + opts.length) % opts.length].value);
    }
  }
}
