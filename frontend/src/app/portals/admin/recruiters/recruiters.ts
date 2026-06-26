import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { RecruiterApprovalService } from '../admin.services';
import { AccountStatus, PendingRecruiter } from '../admin.models';
import { accountStatusLabel } from '../admin.mappers';

const FILTERS: AccountStatus[] = ['PENDING_APPROVAL', 'ACTIVE', 'REJECTED'];

/** Recruiter approvals (Story 9.6) — list by account status, approve / reject-with-reason. */
@Component({
  selector: 'app-admin-recruiters',
  standalone: true,
  imports: [ReactiveFormsModule, StatusPill, Button, Modal],
  template: `
    <h1 class="cc-h2">Recruiters</h1>
    <div class="filters" role="group" aria-label="Filter by status">
      @for (f of filters; track f) {
        <button type="button" class="chip" [class.chip--on]="status() === f" [attr.aria-pressed]="status() === f" (click)="setStatus(f)">{{ label(f) }}</button>
      }
    </div>

    @if (state() === 'loading') {
      <p class="cc-body">Loading…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load recruiters. <button class="link" type="button" (click)="load()">Try again</button></p>
    } @else if (rows().length === 0) {
      <div class="card empty" role="status">
        <span class="empty__icon" aria-hidden="true">🏢</span>
        <p class="empty__title cc-body-medium">No recruiters to review</p>
        <p class="empty__sub cc-small">Recruiter sign-ups awaiting approval will appear here.</p>
      </div>
    } @else {
      <ul class="list">
        @for (r of rows(); track r.userId) {
          <li class="card">
            <div>
              <span class="cc-body-medium">{{ r.companyName }}</span>
              <p class="cc-small muted">{{ r.email }}@if (r.industry) { · {{ r.industry }} }@if (r.recruiterDesignation) { · {{ r.recruiterDesignation }} }@if (r.contactPhone) { · {{ r.contactPhone }} }</p>
              @if (r.companyWebsite) { <p class="cc-small muted">{{ r.companyWebsite }}</p> }
              @if (r.companyDescription) { <p class="cc-small muted">{{ r.companyDescription }}</p> }
            </div>
            <div class="card__actions">
              <app-status-pill [label]="label(status())" [variant]="variant(status())" />
              @if (status() === 'PENDING_APPROVAL') {
                <app-button size="sm" [loading]="acting()" (click)="approve(r)">Approve</app-button>
                <app-button size="sm" variant="danger" (click)="openReject(r)">Reject</app-button>
              }
            </div>
          </li>
        }
      </ul>
    }

    <app-modal [(open)]="rejectOpen" title="Reject recruiter">
      <form [formGroup]="rejectForm">
        <label class="fl">Reason<textarea class="inp" formControlName="reason" rows="3" maxlength="500"></textarea></label>
        @if (rejectForm.controls.reason.touched && rejectForm.invalid) {
          <p class="field-error cc-small" role="alert">A reason is required.</p>
        }
      </form>
      <div footer>
        <app-button variant="ghost" (click)="rejectOpen.set(false)">Cancel</app-button>
        <app-button variant="danger" [loading]="acting()" (click)="doReject()">Reject</app-button>
      </div>
    </app-modal>
  `,
  styles: [
    `
      h1 {
        margin: 0 0 var(--cc-space-4);
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
        align-items: flex-start;
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
        flex: 0 0 auto;
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
export class RecruiterApprovalsPage {
  private readonly fb = inject(FormBuilder);
  private readonly svc = inject(RecruiterApprovalService);
  private readonly toast = inject(ToastService);
  protected readonly filters = FILTERS;

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly rows = signal<PendingRecruiter[]>([]);
  readonly status = signal<AccountStatus>('PENDING_APPROVAL');
  readonly acting = signal(false);
  readonly rejectOpen = signal(false);
  private readonly active = signal<PendingRecruiter | null>(null);

  readonly rejectForm = this.fb.nonNullable.group({ reason: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]] });

  constructor() {
    void this.load();
  }

  label = accountStatusLabel;
  variant(s: AccountStatus) {
    return statusToVariant(s);
  }
  setStatus(s: AccountStatus): void {
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
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not load recruiters.');
    }
  }

  async approve(r: PendingRecruiter): Promise<void> {
    await this.run(() => this.svc.approve(r.userId), 'Recruiter approved.');
  }
  openReject(r: PendingRecruiter): void {
    this.active.set(r);
    this.rejectForm.reset({ reason: '' });
    this.rejectOpen.set(true);
  }
  async doReject(): Promise<void> {
    const r = this.active();
    if (!r || this.rejectForm.invalid) {
      this.rejectForm.markAllAsTouched();
      return;
    }
    await this.run(() => this.svc.reject(r.userId, this.rejectForm.getRawValue().reason.trim()), 'Recruiter rejected.');
    this.rejectOpen.set(false);
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
