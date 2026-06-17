import { Component, inject, signal } from '@angular/core';
import {
  Button,
  Modal,
  StatusPill,
  Stepper,
  ToastService,
  statusToVariant,
} from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { StudentApplication } from '../student.models';
import { ApplicationsService } from '../student.services';
import { applicationStatusLabel, applicationSteps, canWithdraw } from '../student.mappers';

type LoadState = 'loading' | 'error' | 'ready';

/**
 * My Applications (Story 9.4, AC8) — the student's application timeline. Loads the list on init into a
 * signal with five render states (loading, error+retry, empty coaching copy, and the card list). Each
 * card shows company/role/applied-date, a status pill, and the lifecycle stepper. Pre-shortlist
 * applications expose a danger Withdraw action behind a confirm modal; the async result is announced via
 * a toast. Token-styled only — no hard-coded colors.
 */
@Component({
  selector: 'app-student-applications',
  standalone: true,
  imports: [StatusPill, Stepper, Button, Modal],
  template: `
    <section class="page">
      <h1 class="cc-h2">My Applications</h1>

      @if (state() === 'loading') {
        <p class="muted" role="status">Loading…</p>
      } @else if (state() === 'error') {
        <div class="panel" role="alert">
          <p class="muted">We couldn't load your applications.</p>
          <app-button variant="secondary" size="sm" (click)="reload()">Retry</app-button>
        </div>
      } @else if (applications().length === 0) {
        <div class="panel empty">
          <p class="muted">No applications yet — apply to an eligible drive and it'll show up here.</p>
        </div>
      } @else {
        <ul class="cards">
          @for (app of applications(); track app.id) {
            <li class="card">
              <div class="card__head">
                <div class="card__title">
                  <span class="cc-body-medium company">{{ app.companyName ?? 'Company' }}</span>
                  @if (app.role) {
                    <span class="muted role">{{ app.role }}</span>
                  }
                </div>
                <app-status-pill
                  [label]="label(app.status)"
                  [variant]="variant(app.status)"
                />
              </div>

              <p class="muted applied">Applied {{ appliedOn(app.appliedAt) }}</p>

              <app-stepper [steps]="steps(app.status)" />

              @if (withdrawable(app.status)) {
                <div class="card__actions">
                  <app-button variant="danger" size="sm" (click)="openWithdraw(app)">Withdraw</app-button>
                </div>
              }
            </li>
          }
        </ul>
      }
    </section>

    <app-modal
      [(open)]="confirmOpen"
      title="Withdraw application"
      (closed)="confirmOpen.set(false)"
    >
      <p class="cc-body">Withdraw this application? This can't be undone.</p>
      <div footer>
        <app-button variant="secondary" size="sm" (click)="confirmOpen.set(false)">Cancel</app-button>
        <app-button variant="danger" size="sm" [loading]="withdrawing()" (click)="confirmWithdraw()">
          Withdraw
        </app-button>
      </div>
    </app-modal>
  `,
  styles: [
    `
      .page {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-6);
      }
      .cards {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
      }
      .card {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
        padding: var(--cc-space-6);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
      }
      .card__head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--cc-space-4);
      }
      .card__title {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-1);
      }
      .company {
        color: var(--cc-color-text);
      }
      .card__actions {
        display: flex;
        justify-content: flex-end;
      }
      .panel {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--cc-space-3);
        padding: var(--cc-space-6);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
      }
      .empty {
        align-items: flex-start;
      }
      .muted {
        margin: 0;
        font: var(--cc-text-body);
        color: var(--cc-color-text-secondary);
      }
    `,
  ],
})
export class ApplicationsPage {
  private readonly service = inject(ApplicationsService);
  private readonly toast = inject(ToastService);

  readonly state = signal<LoadState>('loading');
  readonly applications = signal<StudentApplication[]>([]);
  readonly confirmOpen = signal(false);
  readonly withdrawing = signal(false);
  private readonly pending = signal<StudentApplication | null>(null);

  // Template-bound mapper passthroughs (keep the markup declarative).
  readonly label = applicationStatusLabel;
  readonly variant = (s: StudentApplication['status']) => statusToVariant(s);
  readonly steps = applicationSteps;
  readonly withdrawable = canWithdraw;

  constructor() {
    void this.reload();
  }

  /** Load the application list into the signal, toggling the loading/error/ready state. */
  async reload(): Promise<void> {
    this.state.set('loading');
    try {
      const list = await this.service.listApplications();
      this.applications.set(list);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  appliedOn(iso: string): string {
    return new Date(iso).toLocaleDateString();
  }

  /** Open the confirm modal for a specific application. */
  openWithdraw(app: StudentApplication): void {
    this.pending.set(app);
    this.confirmOpen.set(true);
  }

  /**
   * Confirm the withdraw — calls the service, replaces the card from the returned application on success
   * and toasts, maps a thrown ApiResponseError to plain copy on failure. The modal closes either way.
   */
  async confirmWithdraw(): Promise<void> {
    const target = this.pending();
    if (!target) {
      this.confirmOpen.set(false);
      return;
    }
    this.withdrawing.set(true);
    try {
      const updated = await this.service.withdraw(target.id);
      this.applications.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
      this.toast.success('Application withdrawn.');
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not withdraw.');
    } finally {
      this.withdrawing.set(false);
      this.pending.set(null);
      this.confirmOpen.set(false);
    }
  }
}
