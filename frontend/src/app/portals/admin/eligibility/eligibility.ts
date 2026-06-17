import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button, TextField, ToastService } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { EligibilityPolicyService } from '../admin.services';
import { EligibilityPolicy, UpdateEligibilityPolicyRequest } from '../admin.models';

const NUMERIC = /^\d*\.?\d+$/;

/**
 * Admin "Eligibility policy" screen (Story 9.6, AC7). Edits the two policy fields the PUT contract
 * accepts (minimum CGPA floor, re-apply package threshold); the placedStudentsMayApply flag is shown
 * read-only because the update request excludes it. Token-styled — no hard-coded hex.
 */
@Component({
  selector: 'app-admin-eligibility',
  standalone: true,
  imports: [ReactiveFormsModule, Button, TextField],
  template: `
    <h1 class="cc-h2">Eligibility policy</h1>

    @if (state() === 'loading') {
      <div class="card sk"></div>
    } @else if (state() === 'error') {
      <div class="card">
        <p class="cc-body">
          We couldn't load the eligibility policy.
          <button class="link" type="button" (click)="load()">Try again</button>
        </p>
      </div>
    } @else {
      <form class="card" [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (formError()) {
          <p class="form-error" role="alert">{{ formError() }}</p>
        }

        <app-text-field
          label="Minimum CGPA floor"
          formControlName="minCgpaFloor"
          inputmode="decimal"
          hint="Optional. Leave blank for no floor."
          [error]="cgpaError()"
        />
        <app-text-field
          label="Re-apply package threshold (LPA)"
          formControlName="reapplyPackageThresholdLpa"
          inputmode="decimal"
          hint="Optional. Leave blank to disable the threshold."
          [error]="thresholdError()"
        />

        <p class="readonly cc-body">
          Placed students may apply: <strong>{{ placedMayApply() ? 'Yes' : 'No' }}</strong>
        </p>

        <app-button type="submit" [loading]="saving()">Save</app-button>
      </form>
    }
  `,
  styles: [
    `
      h1 {
        margin: 0 0 var(--cc-space-6);
      }
      .card {
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
        padding: var(--cc-space-6);
        max-width: 520px;
      }
      .sk {
        height: 220px;
        background: var(--cc-color-surface);
      }
      form {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
      }
      .form-error {
        margin: 0;
        padding: var(--cc-space-3);
        font: var(--cc-text-small);
        color: var(--cc-color-danger);
        background: var(--cc-color-danger-subtle);
        border-radius: var(--cc-radius-sm);
      }
      .readonly {
        margin: 0;
        color: var(--cc-color-text-secondary);
      }
      .link {
        background: none;
        border: none;
        padding: 0;
        color: var(--cc-color-primary);
        cursor: pointer;
        text-decoration: underline;
      }
    `,
  ],
})
export class EligibilityPolicyPage {
  private readonly fb = inject(FormBuilder);
  private readonly svc = inject(EligibilityPolicyService);
  private readonly toast = inject(ToastService);

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly placedMayApply = signal(false);

  readonly form = this.fb.nonNullable.group({
    minCgpaFloor: ['', [Validators.pattern(NUMERIC), Validators.min(0), Validators.max(10)]],
    reapplyPackageThresholdLpa: ['', [Validators.pattern(NUMERIC), Validators.min(0)]],
  });

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.state.set('loading');
    this.formError.set(null);
    try {
      this.applyPolicy(await this.svc.get());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  cgpaError(): string {
    const c = this.form.controls.minCgpaFloor;
    if ((c.touched || c.dirty) && c.invalid) {
      return 'Enter a value between 0 and 10.';
    }
    return '';
  }

  thresholdError(): string {
    const c = this.form.controls.reapplyPackageThresholdLpa;
    if ((c.touched || c.dirty) && c.invalid) {
      return 'Enter a number of 0 or more.';
    }
    return '';
  }

  async save(): Promise<void> {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    try {
      const updated = await this.svc.update(this.toRequest());
      this.applyPolicy(updated);
      this.toast.success('Eligibility policy updated.');
    } catch (e) {
      this.formError.set(toAuthErrorView(e).formMessage ?? 'Could not update the policy.');
    } finally {
      this.saving.set(false);
    }
  }

  private toRequest(): UpdateEligibilityPolicyRequest {
    const v = this.form.getRawValue();
    return {
      minCgpaFloor: this.toNumberOrNull(v.minCgpaFloor),
      reapplyPackageThresholdLpa: this.toNumberOrNull(v.reapplyPackageThresholdLpa),
    };
  }

  private toNumberOrNull(raw: string): number | null {
    const trimmed = raw.trim();
    return trimmed === '' ? null : Number(trimmed);
  }

  private applyPolicy(policy: EligibilityPolicy): void {
    this.placedMayApply.set(policy.placedStudentsMayApply);
    this.form.reset({
      minCgpaFloor: this.toFieldValue(policy.minCgpaFloor),
      reapplyPackageThresholdLpa: this.toFieldValue(policy.reapplyPackageThresholdLpa),
    });
  }

  private toFieldValue(value: number | null | undefined): string {
    return value === null || value === undefined ? '' : String(value);
  }
}
