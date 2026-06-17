import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { DriveService } from '../recruiter.services';
import { DriveResponse } from '../recruiter.models';
import { driveStatusLabel, isDriveEditable } from '../recruiter.mappers';
import { DriveForm } from './drive-form';
import { RecruiterApplicants } from './applicants';
import { RecruiterRounds } from './rounds';
import { RecruiterOffers } from './offers';

type Tab = 'overview' | 'applicants' | 'interviews' | 'offers';

/**
 * Drive workspace (Story 9.5) — loads a single drive and hosts the per-drive tabs: Overview (edit form +
 * submit/cancel), Applicants, Interviews, Offers. The company/role + status pill stay visible across tabs.
 */
@Component({
  selector: 'app-recruiter-workspace',
  standalone: true,
  imports: [RouterLink, StatusPill, Button, Modal, DriveForm, RecruiterApplicants, RecruiterRounds, RecruiterOffers],
  template: `
    @if (state() === 'loading') {
      <p class="cc-body">Loading drive…</p>
    } @else if (state() === 'notfound') {
      <p class="cc-body">That drive wasn't found. <a class="link" routerLink="/recruiter/drives">Back to My Drives</a></p>
    } @else if (drive(); as d) {
      <div class="head">
        <a class="link cc-small" routerLink="/recruiter/drives">← My Drives</a>
        <div class="head__title">
          <h1 class="cc-h2">{{ d.companyName }} · {{ d.role }}</h1>
          <app-status-pill [label]="statusLabel(d.status)" [variant]="variant(d.status)" />
        </div>
        @if (d.status === 'REJECTED_BY_ADMIN' && d.rejectionReason) {
          <p class="banner" role="alert">Changes requested: {{ d.rejectionReason }}</p>
        }
        <div class="head__actions">
          @if (editable()) {
            <app-button size="sm" [loading]="acting()" (click)="submit()">Submit for approval</app-button>
          }
          <app-button size="sm" variant="danger" (click)="confirmCancel.set(true)">Cancel drive</app-button>
        </div>
      </div>

      <nav class="tabs" role="tablist">
        @for (t of tabs; track t.key) {
          <button type="button" class="tab" role="tab" [class.tab--on]="tab() === t.key" [attr.aria-selected]="tab() === t.key" (click)="tab.set(t.key)">
            {{ t.label }}
          </button>
        }
      </nav>

      <section class="panel">
        @switch (tab()) {
          @case ('overview') {
            <app-recruiter-drive-form [drive]="d" (saved)="onSaved($event)" />
          }
          @case ('applicants') {
            <app-recruiter-applicants [driveId]="d.id" />
          }
          @case ('interviews') {
            <app-recruiter-rounds [driveId]="d.id" />
          }
          @case ('offers') {
            <app-recruiter-offers [driveId]="d.id" [drive]="d" />
          }
        }
      </section>

      <app-modal [(open)]="confirmCancel" title="Cancel drive">
        <p class="cc-body">Cancel this drive? This can't be undone.</p>
        <div footer>
          <app-button variant="ghost" (click)="confirmCancel.set(false)">Keep drive</app-button>
          <app-button variant="danger" [loading]="acting()" (click)="cancel()">Cancel drive</app-button>
        </div>
      </app-modal>
    }
  `,
  styles: [
    `
      .head {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-3);
        margin-bottom: var(--cc-space-5);
      }
      .head__title {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
      }
      .head__title h1 {
        margin: 0;
      }
      .head__actions {
        display: flex;
        gap: var(--cc-space-3);
      }
      .banner {
        margin: 0;
        padding: var(--cc-space-3) var(--cc-space-4);
        font: var(--cc-text-small);
        color: var(--cc-color-danger);
        background: var(--cc-color-danger-subtle);
        border-radius: var(--cc-radius-sm);
      }
      .tabs {
        display: flex;
        gap: var(--cc-space-2);
        border-bottom: 1px solid var(--cc-color-border);
        margin-bottom: var(--cc-space-6);
      }
      .tab {
        font: var(--cc-text-body-medium);
        background: none;
        border: none;
        border-bottom: 2px solid transparent;
        color: var(--cc-color-text-secondary);
        padding: var(--cc-space-3) var(--cc-space-2);
        cursor: pointer;
      }
      .tab--on {
        color: var(--cc-color-primary);
        border-bottom-color: var(--cc-color-primary);
      }
      .link {
        color: var(--cc-color-primary);
        text-decoration: none;
      }
    `,
  ],
})
export class DriveWorkspacePage {
  private readonly route = inject(ActivatedRoute);
  private readonly driveSvc = inject(DriveService);
  private readonly toast = inject(ToastService);

  protected readonly tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'applicants', label: 'Applicants' },
    { key: 'interviews', label: 'Interviews' },
    { key: 'offers', label: 'Offers' },
  ];

  readonly state = signal<'loading' | 'notfound' | 'ready'>('loading');
  readonly drive = signal<DriveResponse | null>(null);
  readonly tab = signal<Tab>('overview');
  readonly acting = signal(false);
  readonly confirmCancel = signal(false);

  readonly editable = computed(() => {
    const d = this.drive();
    return !!d && isDriveEditable(d.status);
  });

  constructor() {
    const id = this.route.snapshot.paramMap.get('driveId');
    if (id) {
      void this.load(id);
    } else {
      this.state.set('notfound');
    }
  }

  statusLabel = driveStatusLabel;
  variant(s: DriveResponse['status']) {
    return statusToVariant(s);
  }

  private async load(id: string): Promise<void> {
    this.state.set('loading');
    try {
      this.drive.set(await this.driveSvc.get(id));
      this.state.set('ready');
    } catch {
      this.state.set('notfound');
    }
  }

  onSaved(updated: DriveResponse): void {
    this.drive.set(updated);
  }

  async submit(): Promise<void> {
    const d = this.drive();
    if (!d) {
      return;
    }
    this.acting.set(true);
    try {
      this.drive.set(await this.driveSvc.submit(d.id));
      this.toast.success('Drive submitted for approval.');
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not submit the drive.');
    } finally {
      this.acting.set(false);
    }
  }

  async cancel(): Promise<void> {
    const d = this.drive();
    if (!d) {
      return;
    }
    this.acting.set(true);
    try {
      this.drive.set(await this.driveSvc.cancel(d.id));
      this.toast.success('Drive cancelled.');
      this.confirmCancel.set(false);
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not cancel the drive.');
    } finally {
      this.acting.set(false);
    }
  }
}
