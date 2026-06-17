import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { ApplicantService, OfferService } from '../recruiter.services';
import { ApplicantSummary, DriveResponse } from '../recruiter.models';
import { applicantStatusLabel, OFFER_STATUSES } from '../recruiter.mappers';
import { futureDateTime, positiveNumber } from '../../../shared/forms/validators';

/**
 * Offers tab (Story 9.5) — release an offer (PDF + terms) to a SELECTED applicant, and track acceptance by
 * listing the drive's offer-status applicants (no recruiter offers-list endpoint, so we use the applicant
 * query filtered to SELECTED + the offer lifecycle statuses).
 */
@Component({
  selector: 'app-recruiter-offers',
  standalone: true,
  imports: [ReactiveFormsModule, StatusPill, Button, Modal],
  template: `
    @if (state() === 'loading') {
      <p class="cc-body">Loading offers…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load this. <button class="link" type="button" (click)="load()">Try again</button></p>
    } @else if (rows().length === 0) {
      <p class="empty cc-body" role="status">No selected applicants yet — select final candidates from the Applicants tab to release offers.</p>
    } @else {
      <ul class="list">
        @for (a of rows(); track a.applicationId) {
          <li class="row">
            <span class="cc-body-medium">{{ a.fullName }} <span class="muted">· {{ a.rollNumber }}</span></span>
            @if (a.status === 'SELECTED') {
              <app-button size="sm" (click)="openRelease(a)">Release offer</app-button>
            } @else {
              <app-status-pill [label]="label(a.status)" [variant]="variant(a.status)" />
            }
          </li>
        }
      </ul>
    }

    <app-modal [(open)]="releaseOpen" title="Release offer">
      <form class="form" [formGroup]="form">
        @if (formError()) {
          <p class="form-error" role="alert">{{ formError() }}</p>
        }
        <label class="fl">Role<input class="inp" formControlName="role" /></label>
        <label class="fl">CTC (LPA)<input class="inp" type="text" inputmode="decimal" formControlName="ctc" /></label>
        @if (fieldInvalid('ctc')) {
          <p class="field-error cc-small" role="alert">Enter a CTC greater than 0.</p>
        }
        <label class="fl">Joining date<input class="inp" formControlName="joiningDate" placeholder="YYYY-MM-DDTHH:mm:ssZ" /></label>
        @if (fieldInvalid('joiningDate')) {
          <p class="field-error cc-small" role="alert">Enter a valid joining date in the future.</p>
        }
        <label class="fl">Acceptance deadline<input class="inp" formControlName="acceptanceDeadline" placeholder="YYYY-MM-DDTHH:mm:ssZ" /></label>
        @if (fieldInvalid('acceptanceDeadline')) {
          <p class="field-error cc-small" role="alert">Enter a valid deadline in the future.</p>
        }
        <label class="fl">Offer letter (PDF)<input class="inp" type="file" accept="application/pdf" (change)="onFile($event)" /></label>
      </form>
      <div footer>
        <app-button variant="ghost" (click)="releaseOpen.set(false)">Cancel</app-button>
        <app-button [loading]="releasing()" (click)="release()">Release offer</app-button>
      </div>
    </app-modal>
  `,
  styles: [
    `
      .list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-2);
      }
      .row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-3);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-md);
        padding: var(--cc-space-3) var(--cc-space-4);
      }
      .muted {
        color: var(--cc-color-text-secondary);
      }
      .empty {
        margin-top: var(--cc-space-8);
        color: var(--cc-color-text-secondary);
      }
      .form {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-3);
      }
      .fl {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-1);
        font: var(--cc-text-body-medium);
      }
      .inp {
        font: var(--cc-text-body);
        border: 1px solid var(--cc-color-border-strong);
        border-radius: var(--cc-radius-sm);
        padding: var(--cc-space-2) var(--cc-space-3);
        min-height: 38px;
      }
      .form-error {
        margin: 0;
        padding: var(--cc-space-3);
        font: var(--cc-text-small);
        color: var(--cc-color-danger);
        background: var(--cc-color-danger-subtle);
        border-radius: var(--cc-radius-sm);
      }
      .field-error {
        margin: calc(var(--cc-space-1) * -1) 0 0;
        color: var(--cc-color-danger);
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
export class RecruiterOffers {
  private readonly fb = inject(FormBuilder);
  private readonly applicantSvc = inject(ApplicantService);
  private readonly offerSvc = inject(OfferService);
  private readonly toast = inject(ToastService);

  readonly driveId = input.required<string>();
  readonly drive = input<DriveResponse | null>(null);

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly rows = signal<ApplicantSummary[]>([]);
  readonly releaseOpen = signal(false);
  readonly releasing = signal(false);
  readonly formError = signal<string | null>(null);
  private readonly active = signal<ApplicantSummary | null>(null);
  private file: File | null = null;

  readonly form = this.fb.nonNullable.group({
    role: ['', Validators.required],
    ctc: ['', [Validators.required, Validators.pattern(/^\d*\.?\d+$/), positiveNumber]],
    joiningDate: ['', [Validators.required, futureDateTime]],
    acceptanceDeadline: ['', [Validators.required, futureDateTime]],
  });

  constructor() {
    queueMicrotask(() => this.load());
  }

  label = applicantStatusLabel;
  variant(s: ApplicantSummary['status']) {
    return statusToVariant(s);
  }
  /** True once the field is touched/dirty and currently invalid — gates its inline error message. */
  fieldInvalid(name: 'ctc' | 'joiningDate' | 'acceptanceDeadline'): boolean {
    const c = this.form.controls[name];
    return (c.touched || c.dirty) && c.invalid;
  }

  async load(): Promise<void> {
    this.state.set('loading');
    try {
      const page = await this.applicantSvc.list(this.driveId(), { status: ['SELECTED', ...OFFER_STATUSES], pageSize: 200 });
      this.rows.set(page.items);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  openRelease(a: ApplicantSummary): void {
    this.active.set(a);
    this.file = null;
    this.formError.set(null);
    this.form.reset({ role: this.drive()?.role ?? '', ctc: this.drive()?.packageLpa != null ? String(this.drive()!.packageLpa) : '', joiningDate: '', acceptanceDeadline: '' });
    this.releaseOpen.set(true);
  }

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  async release(): Promise<void> {
    const a = this.active();
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError.set('Fill in every field.');
      return;
    }
    if (!this.file || this.file.type !== 'application/pdf') {
      this.formError.set('Attach the offer letter as a PDF.');
      return;
    }
    if (!a) {
      return;
    }
    this.releasing.set(true);
    try {
      const v = this.form.getRawValue();
      await this.offerSvc.release(this.driveId(), a.applicationId, {
        role: v.role.trim(),
        ctc: Number(v.ctc),
        joiningDate: v.joiningDate.trim(),
        acceptanceDeadline: v.acceptanceDeadline.trim(),
      }, this.file);
      this.toast.success('Offer released.');
      this.releaseOpen.set(false);
      await this.load();
    } catch (e) {
      this.formError.set(toAuthErrorView(e).formMessage ?? 'Could not release the offer.');
    } finally {
      this.releasing.set(false);
    }
  }
}
