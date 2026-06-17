import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Button, TextField, ToastService } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { DriveService } from '../recruiter.services';
import { DriveRequest, DriveResponse } from '../recruiter.models';
import { isDriveEditable } from '../recruiter.mappers';

/**
 * Drive create/edit form (Story 9.5). A reusable reactive form used by the /new page and the workspace
 * Overview tab. `PUT` is a FULL REPLACE, so the form always submits the complete editable state; a drive
 * that is not DRAFT/REJECTED_BY_ADMIN renders read-only.
 */
@Component({
  selector: 'app-recruiter-drive-form',
  standalone: true,
  imports: [ReactiveFormsModule, Button, TextField],
  template: `
    <form class="form" [formGroup]="form">
      @if (formError()) {
        <p class="form-error" role="alert">{{ formError() }}</p>
      }
      <app-text-field label="Role" formControlName="role" [error]="err('role', 'Role')" />
      <app-text-field label="Package (LPA)" type="text" inputmode="decimal" formControlName="packageLpa" [error]="err('packageLpa', 'Package')" />
      <app-text-field label="Location" formControlName="location" />
      <app-text-field label="Openings" type="text" inputmode="numeric" formControlName="openings" [error]="err('openings', 'Openings')" />
      <app-text-field label="Apply deadline" formControlName="applyDeadline" hint="YYYY-MM-DDTHH:mm:ssZ" />

      <fieldset class="elig" formGroupName="eligibility">
        <legend class="cc-h3">Eligibility</legend>
        <app-text-field label="Branches" formControlName="branches" hint="Comma-separated, e.g. CSE, ECE" />
        <app-text-field label="Minimum CGPA" type="text" inputmode="decimal" formControlName="minCgpa" [error]="err('eligibility.minCgpa', 'Minimum CGPA')" />
        <label class="select-field">
          <span class="select-field__label cc-body-medium">Backlog policy</span>
          <select class="select-field__input" formControlName="backlogPolicy">
            <option value="NO_BACKLOG">No active backlogs</option>
            <option value="ALLOW_BACKLOG">Backlogs allowed</option>
          </select>
        </label>
        <app-text-field label="Batch" formControlName="batch" />
      </fieldset>

      @if (!readOnly()) {
        <div class="actions">
          <app-button [loading]="saving()" (click)="save()">{{ drive() ? 'Save changes' : 'Create draft' }}</app-button>
        </div>
      } @else {
        <p class="cc-small muted">This drive can no longer be edited.</p>
      }
    </form>
  `,
  styles: [
    `
      .form {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
        max-width: 560px;
      }
      .form-error {
        margin: 0;
        padding: var(--cc-space-3);
        font: var(--cc-text-small);
        color: var(--cc-color-danger);
        background: var(--cc-color-danger-subtle);
        border-radius: var(--cc-radius-sm);
      }
      .elig {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-md);
        padding: var(--cc-space-4);
      }
      .elig legend {
        padding: 0 var(--cc-space-2);
      }
      .select-field {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-1);
      }
      .select-field__input {
        font: var(--cc-text-body);
        color: var(--cc-color-text);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border-strong);
        border-radius: var(--cc-radius-sm);
        padding: var(--cc-space-2) var(--cc-space-3);
        min-height: 40px;
      }
      .select-field__input:focus-visible {
        outline: 2px solid var(--cc-color-primary);
        border-color: var(--cc-color-primary);
      }
      .actions {
        display: flex;
        justify-content: flex-end;
      }
      .muted {
        color: var(--cc-color-text-secondary);
      }
    `,
  ],
})
export class DriveForm {
  private readonly fb = inject(FormBuilder);
  private readonly driveSvc = inject(DriveService);
  private readonly toast = inject(ToastService);

  /** When set, the form edits this drive; when null, it creates a new draft. */
  readonly drive = input<DriveResponse | null>(null);
  readonly saved = output<DriveResponse>();

  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  private readonly submitted = signal(false);

  readonly form = this.fb.nonNullable.group({
    role: ['', Validators.required],
    packageLpa: ['', [Validators.pattern(/^\d*\.?\d+$/), Validators.min(0)]],
    location: [''],
    openings: ['', [Validators.pattern(/^\d+$/), Validators.min(1)]],
    applyDeadline: [''],
    eligibility: this.fb.nonNullable.group({
      branches: [''],
      minCgpa: ['', [Validators.pattern(/^\d*\.?\d+$/), Validators.min(0), Validators.max(10)]],
      backlogPolicy: ['NO_BACKLOG'],
      batch: [''],
    }),
  });

  readOnly(): boolean {
    const d = this.drive();
    return !!d && !isDriveEditable(d.status);
  }

  private seeded = false;

  constructor() {
    // Seed the form once the `drive` input first binds; and on every drive change, reconcile the
    // enabled/disabled state so a status change (e.g. DRAFT→PENDING_APPROVAL after submit) makes the
    // Overview form read-only even when the component instance is reused.
    effect(() => {
      const d = this.drive();
      if (!d) {
        return;
      }
      if (!this.seeded) {
        this.seeded = true;
        this.seed(d);
      }
      if (this.readOnly() && this.form.enabled) {
        this.form.disable({ emitEvent: false });
      } else if (!this.readOnly() && this.form.disabled) {
        this.form.enable({ emitEvent: false });
      }
    });
  }

  private seed(d: DriveResponse): void {
    this.form.patchValue({
      role: d.role ?? '',
      packageLpa: d.packageLpa != null ? String(d.packageLpa) : '',
      location: d.location ?? '',
      openings: d.openings != null ? String(d.openings) : '',
      applyDeadline: d.applyDeadline ?? '',
      eligibility: {
        branches: (d.eligibility.branches ?? []).join(', '),
        minCgpa: d.eligibility.minCgpa != null ? String(d.eligibility.minCgpa) : '',
        backlogPolicy: d.eligibility.backlogPolicy ?? 'NO_BACKLOG',
        batch: d.eligibility.batch ?? '',
      },
    });
  }

  err(path: string, label: string): string {
    const c = this.form.get(path);
    if (c && (this.submitted() || c.touched) && c.invalid) {
      return c.errors?.['required'] ? `${label} is required.` : `${label} is invalid.`;
    }
    return '';
  }

  private buildRequest(): DriveRequest {
    const v = this.form.getRawValue();
    const num = (s: string): number | null => (s.trim() === '' ? null : Number(s));
    const str = (s: string): string | null => (s.trim() === '' ? null : s.trim());
    return {
      role: str(v.role),
      packageLpa: num(v.packageLpa),
      location: str(v.location),
      openings: num(v.openings),
      applyDeadline: str(v.applyDeadline),
      eligibility: {
        branches: v.eligibility.branches.trim() ? v.eligibility.branches.split(',').map((s) => s.trim()).filter(Boolean) : null,
        minCgpa: num(v.eligibility.minCgpa),
        backlogPolicy: v.eligibility.backlogPolicy as DriveRequest['eligibility']['backlogPolicy'],
        batch: str(v.eligibility.batch),
      },
    };
  }

  async save(): Promise<void> {
    this.submitted.set(true);
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    try {
      const body = this.buildRequest();
      const existing = this.drive();
      const result = existing ? await this.driveSvc.update(existing.id, body) : await this.driveSvc.create(body);
      this.toast.success(existing ? 'Drive updated.' : 'Drive draft created.');
      this.saved.emit(result);
    } catch (e) {
      this.formError.set(toAuthErrorView(e).formMessage ?? 'Could not save the drive.');
    } finally {
      this.saving.set(false);
    }
  }
}

/** The /recruiter/drives/new page — a create-mode wrapper that navigates to the workspace on success. */
@Component({
  selector: 'app-recruiter-drive-form-page',
  standalone: true,
  imports: [DriveForm],
  template: `
    <h1 class="cc-h2">Create a drive</h1>
    <app-recruiter-drive-form (saved)="onSaved($event)" />
  `,
  styles: [`h1 { margin: 0 0 var(--cc-space-6); }`],
})
export class DriveFormPage {
  private readonly router = inject(Router);
  onSaved(drive: DriveResponse): void {
    void this.router.navigate(['/recruiter/drives', drive.id]);
  }
}
