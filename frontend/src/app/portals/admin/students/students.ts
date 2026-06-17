import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { ProfileApprovalService } from '../admin.services';
import { PendingProfile, ProfileApprovalStatus } from '../admin.models';
import { profileStatusLabel } from '../admin.mappers';

const FILTERS: ProfileApprovalStatus[] = ['PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'DRAFT'];

/**
 * Student-profile approvals (Story 9.6) — list by status, approve / reject-with-reason / edit academics,
 * and lock or unlock the season. Lists are unpaged; a status filter re-queries.
 */
@Component({
  selector: 'app-admin-students',
  standalone: true,
  imports: [ReactiveFormsModule, StatusPill, Button, Modal],
  template: `
    <div class="head">
      <h1 class="cc-h2">Student profiles</h1>
      <div class="head__actions">
        <app-button size="sm" variant="secondary" [loading]="acting()" (click)="askLock('lock')">Lock season</app-button>
        <app-button size="sm" variant="ghost" [loading]="acting()" (click)="askLock('unlock')">Unlock</app-button>
      </div>
    </div>

    <div class="filters" role="group" aria-label="Filter by status">
      @for (f of filters; track f) {
        <button type="button" class="chip" [class.chip--on]="status() === f" [attr.aria-pressed]="status() === f" (click)="setStatus(f)">{{ label(f) }}</button>
      }
    </div>

    @if (state() === 'loading') {
      <p class="cc-body">Loading…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load profiles. <button class="link" type="button" (click)="load()">Try again</button></p>
    } @else if (rows().length === 0) {
      <p class="empty cc-body" role="status">Nothing pending here — you're all caught up.</p>
    } @else {
      <ul class="list">
        @for (p of rows(); track p.studentId) {
          <li class="card">
            <div>
              <span class="cc-body-medium">{{ p.fullName }} <span class="muted">· {{ p.rollNumber }}</span></span>
              <p class="cc-small muted">{{ p.branch }} · {{ p.batch }} · CGPA {{ p.cgpa }} · {{ p.activeBacklogs }} backlogs · {{ p.completionPercent }}% complete</p>
            </div>
            <div class="card__actions">
              <app-status-pill [label]="label(status())" [variant]="variant(status())" />
              <app-button size="sm" variant="ghost" (click)="openEdit(p)">Edit</app-button>
              @if (status() === 'PENDING_APPROVAL') {
                <app-button size="sm" (click)="approve(p)" [loading]="acting()">Approve</app-button>
                <app-button size="sm" variant="danger" (click)="openReject(p)">Reject</app-button>
              }
            </div>
          </li>
        }
      </ul>
    }

    <app-modal [(open)]="rejectOpen" title="Reject profile">
      <form [formGroup]="rejectForm">
        <label class="fl">Reason (the student sees this)
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

    <app-modal [(open)]="editOpen" title="Edit academics">
      <form class="form" [formGroup]="editForm">
        <label class="fl">Branch<input class="inp" formControlName="branch" /></label>
        <label class="fl">CGPA<input class="inp" type="text" inputmode="decimal" formControlName="cgpa" /></label>
        <label class="fl">Active backlogs<input class="inp" type="text" inputmode="numeric" formControlName="activeBacklogs" /></label>
        <label class="fl">Batch<input class="inp" formControlName="batch" /></label>
        @if (editForm.invalid && editForm.dirty) {
          <p class="field-error cc-small" role="alert">CGPA must be 0–10 and backlogs a whole number.</p>
        }
      </form>
      <div footer>
        <app-button variant="ghost" (click)="editOpen.set(false)">Cancel</app-button>
        <app-button [loading]="acting()" (click)="doEdit()">Save</app-button>
      </div>
    </app-modal>

    <app-modal [(open)]="lockOpen" [title]="lockKind() === 'lock' ? 'Lock the season' : 'Unlock the season'">
      <p class="cc-body">{{ lockKind() === 'lock' ? 'Lock all profiles for editing this season?' : 'Unlock all profiles for editing?' }}</p>
      <div footer>
        <app-button variant="ghost" (click)="lockOpen.set(false)">Cancel</app-button>
        <app-button [loading]="acting()" (click)="doLock()">{{ lockKind() === 'lock' ? 'Lock' : 'Unlock' }}</app-button>
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
      .head__actions {
        display: flex;
        gap: var(--cc-space-2);
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
export class StudentApprovalsPage {
  private readonly fb = inject(FormBuilder);
  private readonly svc = inject(ProfileApprovalService);
  private readonly toast = inject(ToastService);
  protected readonly filters = FILTERS;

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly rows = signal<PendingProfile[]>([]);
  readonly status = signal<ProfileApprovalStatus>('PENDING_APPROVAL');
  readonly acting = signal(false);
  readonly rejectOpen = signal(false);
  readonly editOpen = signal(false);
  readonly lockOpen = signal(false);
  readonly lockKind = signal<'lock' | 'unlock'>('lock');
  private readonly active = signal<PendingProfile | null>(null);

  readonly rejectForm = this.fb.nonNullable.group({ reason: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]] });
  readonly editForm = this.fb.nonNullable.group({
    branch: [''],
    cgpa: ['', [Validators.pattern(/^\d*\.?\d+$/), Validators.min(0), Validators.max(10)]],
    activeBacklogs: ['', [Validators.pattern(/^\d+$/), Validators.min(0)]],
    batch: [''],
  });

  constructor() {
    void this.load();
  }

  label = profileStatusLabel;
  variant(s: ProfileApprovalStatus) {
    return statusToVariant(s);
  }
  setStatus(s: ProfileApprovalStatus): void {
    this.status.set(s);
    void this.load();
  }

  async load(): Promise<void> {
    this.state.set('loading');
    try {
      this.rows.set(await this.svc.list(this.status()));
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  async approve(p: PendingProfile): Promise<void> {
    await this.run(() => this.svc.approve(p.studentId), 'Profile approved.');
  }

  openReject(p: PendingProfile): void {
    this.active.set(p);
    this.rejectForm.reset({ reason: '' });
    this.rejectOpen.set(true);
  }
  async doReject(): Promise<void> {
    const p = this.active();
    if (!p || this.rejectForm.invalid) {
      this.rejectForm.markAllAsTouched();
      return;
    }
    await this.run(() => this.svc.reject(p.studentId, this.rejectForm.getRawValue().reason.trim()), 'Profile rejected.');
    this.rejectOpen.set(false);
  }

  openEdit(p: PendingProfile): void {
    this.active.set(p);
    this.editForm.setValue({
      branch: p.branch ?? '',
      cgpa: p.cgpa != null ? String(p.cgpa) : '',
      activeBacklogs: p.activeBacklogs != null ? String(p.activeBacklogs) : '',
      batch: p.batch ?? '',
    });
    this.editOpen.set(true);
  }
  async doEdit(): Promise<void> {
    const p = this.active();
    if (!p) {
      return;
    }
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const v = this.editForm.getRawValue();
    const num = (s: string): number | null => (s.trim() === '' ? null : Number(s));
    await this.run(
      () => this.svc.edit(p.studentId, { branch: v.branch.trim() || null, cgpa: num(v.cgpa), activeBacklogs: num(v.activeBacklogs), batch: v.batch.trim() || null }),
      'Profile updated.',
    );
    this.editOpen.set(false);
  }

  askLock(kind: 'lock' | 'unlock'): void {
    this.lockKind.set(kind);
    this.lockOpen.set(true);
  }
  async doLock(): Promise<void> {
    const kind = this.lockKind();
    this.acting.set(true);
    try {
      const count = kind === 'lock' ? await this.svc.lock() : await this.svc.unlock();
      this.toast.success(`${kind === 'lock' ? 'Locked' : 'Unlocked'} ${count} profile${count === 1 ? '' : 's'}.`);
      this.lockOpen.set(false);
      await this.load();
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not complete that action.');
    } finally {
      this.acting.set(false);
    }
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
