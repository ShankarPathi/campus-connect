import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { DriveApprovalService } from '../admin.services';
import { PendingDrive, DriveStatus } from '../admin.models';
import { driveStatusLabel } from '../admin.mappers';

const FILTERS: DriveStatus[] = ['PENDING_APPROVAL', 'PUBLISHED', 'REJECTED_BY_ADMIN'];

/**
 * Drive approvals (Story 9.6) — list drives by status, approve-and-publish,
 * reject-with-reason, or edit the eligibility criteria. Lists are unpaged; a status filter re-queries.
 */
@Component({
  selector: 'app-admin-drives',
  standalone: true,
  imports: [ReactiveFormsModule, StatusPill, Button, Modal],
  template: `
    <div class="head">
      <h1 class="cc-h2">Drives</h1>
    </div>

    <div class="filters" role="group" aria-label="Filter by status">
      @for (f of filters; track f) {
        <button type="button" class="chip" [class.chip--on]="status() === f" [attr.aria-pressed]="status() === f" (click)="setStatus(f)">{{ label(f) }}</button>
      }
    </div>

    @if (state() === 'loading') {
      <p class="cc-body">Loading…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load drives. <button class="link" type="button" (click)="load()">Try again</button></p>
    } @else if (rows().length === 0) {
      <div class="card empty" role="status">
        <span class="empty__icon" aria-hidden="true">📋</span>
        <p class="empty__title cc-body-medium">No drives to review</p>
        <p class="empty__sub cc-small">Drives submitted by recruiters for approval will appear here.</p>
      </div>
    } @else {
      <ul class="list">
        @for (d of rows(); track d.id) {
          <li class="card">
            <div>
              <span class="cc-body-medium">{{ d.companyName }} <span class="muted">· {{ d.role }}</span></span>
              <app-status-pill [label]="driveStatusLabel(d.status)" [variant]="statusToVariant(d.status)" />
              <p class="cc-small muted">{{ d.packageLpa }} LPA · {{ d.location }} · {{ d.openings }} openings · deadline {{ d.applyDeadline ?? '—' }}</p>
              <p class="cc-small muted">{{ (d.eligibility.branches ?? []).join(', ') || 'All branches' }} · min CGPA {{ d.eligibility.minCgpa ?? '—' }} · batch {{ d.eligibility.batch ?? '—' }}</p>
            </div>
            @if (status() === 'PENDING_APPROVAL') {
              <div class="card__actions">
                <app-button size="sm" variant="ghost" (click)="openEdit(d)">Edit criteria</app-button>
                <app-button size="sm" (click)="approve(d)" [loading]="acting()">Approve &amp; publish</app-button>
                <app-button size="sm" variant="danger" (click)="openReject(d)">Reject</app-button>
              </div>
            }
          </li>
        }
      </ul>
    }

    <app-modal [(open)]="rejectOpen" title="Reject drive">
      <form [formGroup]="rejectForm">
        <label class="fl">Reason (the recruiter sees this)
          <textarea class="inp" formControlName="reason" rows="3" maxlength="500"></textarea>
        </label>
        @if (rejectForm.controls.reason.touched && rejectForm.invalid) {
          <p class="field-error cc-small" role="alert">A reason is required.</p>
        }
      </form>
      <div footer>
        <app-button variant="ghost" (click)="rejectOpen.set(false)">Cancel</app-button>
        <app-button variant="danger" [loading]="acting()" (click)="doReject()">Reject</app-button>
      </div>
    </app-modal>

    <app-modal [(open)]="editOpen" title="Edit criteria">
      <form class="form" [formGroup]="editForm">
        <label class="fl">Branches (comma-separated)<input class="inp" formControlName="branches" /></label>
        <label class="fl">Min CGPA<input class="inp" type="text" inputmode="decimal" formControlName="minCgpa" /></label>
        <label class="fl">Batch<input class="inp" formControlName="batch" /></label>
      </form>
      <div footer>
        <app-button variant="ghost" (click)="editOpen.set(false)">Cancel</app-button>
        <app-button [loading]="acting()" (click)="doEdit()">Save</app-button>
      </div>
    </app-modal>
  `,
  styles: [
    `
      .head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: var(--cc-space-4);
      }
      .head h1 {
        margin: 0;
      }
      .filters {
        display: flex;
        flex-wrap: wrap;
        gap: var(--cc-space-2);
        margin-bottom: var(--cc-space-4);
      }
      .chip {
        font: var(--cc-text-caption);
        text-transform: uppercase;
        letter-spacing: 0.04em;
        border: 1px solid var(--cc-color-border-strong);
        background: var(--cc-color-surface-raised);
        color: var(--cc-color-text-secondary);
        border-radius: var(--cc-radius-full);
        padding: 4px 10px;
        cursor: pointer;
      }
      .chip--on {
        background: var(--cc-color-primary-subtle);
        color: var(--cc-color-primary);
        border-color: var(--cc-color-primary);
      }
      .list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-3);
      }
      .card {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-4);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
        padding: var(--cc-space-4) var(--cc-space-5);
      }
      .card__actions {
        display: flex;
        gap: var(--cc-space-2);
      }
      .muted {
        color: var(--cc-color-text-secondary);
        margin: var(--cc-space-1) 0 0;
      }
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: var(--cc-space-2);
        padding: var(--cc-space-10) var(--cc-space-6);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
      }
      .empty__icon {
        font-size: 40px;
        width: 80px;
        height: 80px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--cc-radius-full);
        background: var(--cc-portal-soft, var(--cc-color-primary-subtle));
        margin-bottom: var(--cc-space-2);
      }
      .empty__title {
        margin: 0;
      }
      .empty__sub {
        margin: 0;
        color: var(--cc-color-text-secondary);
        max-width: 380px;
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
      }
      .field-error {
        color: var(--cc-color-danger);
        margin: var(--cc-space-1) 0 0;
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
export class DriveApprovalsPage {
  private readonly fb = inject(FormBuilder);
  private readonly svc = inject(DriveApprovalService);
  private readonly toast = inject(ToastService);
  protected readonly filters = FILTERS;

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly rows = signal<PendingDrive[]>([]);
  readonly status = signal<DriveStatus>('PENDING_APPROVAL');
  readonly acting = signal(false);
  readonly rejectOpen = signal(false);
  readonly editOpen = signal(false);
  private readonly active = signal<PendingDrive | null>(null);

  readonly rejectForm = this.fb.nonNullable.group({ reason: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]] });
  readonly editForm = this.fb.nonNullable.group({
    branches: [''],
    minCgpa: ['', [Validators.pattern(/^\d*\.?\d+$/), Validators.min(0), Validators.max(10)]],
    batch: [''],
  });

  // Template helpers.
  protected readonly driveStatusLabel = driveStatusLabel;
  protected readonly statusToVariant = statusToVariant;

  constructor() {
    void this.load();
  }

  label = driveStatusLabel;
  setStatus(s: DriveStatus): void {
    this.status.set(s);
    void this.load();
  }

  /** Monotonic token so a slow earlier request can't clobber a newer one when filters switch fast (Story 9.7). */
  private loadSeq = 0;
  async load(): Promise<void> {
    const seq = ++this.loadSeq;
    this.state.set('loading');
    try {
      const rows = await this.svc.list(this.status());
      if (seq !== this.loadSeq) {
        return; // a newer load() started — discard this stale response
      }
      this.rows.set(rows);
      this.state.set('ready');
    } catch (e) {
      if (seq !== this.loadSeq) {
        return;
      }
      this.state.set('error');
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not load drives.');
    }
  }

  async approve(d: PendingDrive): Promise<void> {
    await this.run(() => this.svc.approve(d.id), 'Drive approved and published.');
  }

  openReject(d: PendingDrive): void {
    this.active.set(d);
    this.rejectForm.reset({ reason: '' });
    this.rejectOpen.set(true);
  }
  async doReject(): Promise<void> {
    const d = this.active();
    if (!d || this.rejectForm.invalid) {
      this.rejectForm.markAllAsTouched();
      return;
    }
    await this.run(() => this.svc.reject(d.id, this.rejectForm.getRawValue().reason.trim()), 'Drive rejected.');
    this.rejectOpen.set(false);
  }

  openEdit(d: PendingDrive): void {
    this.active.set(d);
    this.editForm.setValue({
      branches: (d.eligibility.branches ?? []).join(', '),
      minCgpa: d.eligibility.minCgpa != null ? String(d.eligibility.minCgpa) : '',
      batch: d.eligibility.batch ?? '',
    });
    this.editOpen.set(true);
  }
  async doEdit(): Promise<void> {
    const d = this.active();
    if (!d) {
      return;
    }
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const v = this.editForm.getRawValue();
    const branches = v.branches
      .split(',')
      .map((b) => b.trim())
      .filter((b) => b.length > 0);
    await this.run(
      () =>
        this.svc.editCriteria(d.id, {
          branches: branches.length > 0 ? branches : null,
          minCgpa: v.minCgpa.trim() === '' ? null : Number(v.minCgpa),
          batch: v.batch.trim() || null,
        }),
      'Criteria updated.',
    );
    this.editOpen.set(false);
  }

  private async run(action: () => Promise<unknown>, okMsg: string): Promise<void> {
    this.acting.set(true);
    try {
      await action();
      this.toast.success(okMsg);
      await this.load();
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not complete that action.');
    } finally {
      this.acting.set(false);
    }
  }
}
