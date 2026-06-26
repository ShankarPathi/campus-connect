import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { DriveService } from '../recruiter.services';
import { DriveResponse } from '../recruiter.models';
import { driveStatusLabel, isDriveEditable } from '../recruiter.mappers';

/**
 * My Drives (Story 9.5) — the recruiter's drives with status, openings, deadline, and per-status actions
 * (open workspace, submit, cancel). Create routes to the new-drive form.
 */
@Component({
  selector: 'app-recruiter-drives-list',
  standalone: true,
  imports: [RouterLink, StatusPill, Button, Modal],
  template: `
    <div class="head">
      <h1 class="cc-h2">My Drives</h1>
      <a routerLink="/recruiter/drives/new"><app-button>Create a drive</app-button></a>
    </div>

    @if (state() === 'loading') {
      <p class="cc-body">Loading drives…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load your drives. <button class="link" type="button" (click)="reload()">Try again</button></p>
    } @else if (drives().length === 0) {
      <div class="card empty" role="status">
        <span class="empty__icon" aria-hidden="true">💼</span>
        <p class="empty__title cc-body-medium">No drives yet</p>
        <p class="empty__sub cc-small">Create your first drive to start hiring campus talent.</p>
        <a routerLink="/recruiter/drives/new"><app-button size="sm">Create a drive</app-button></a>
      </div>
    } @else {
      <ul class="list">
        @for (d of drives(); track d.id) {
          <li class="card">
            <div class="card__main">
              <div class="card__top">
                <a class="cc-h3 link-plain" [routerLink]="['/recruiter/drives', d.id]">{{ d.companyName }} · {{ d.role }}</a>
                <app-status-pill [label]="label(d.status)" [variant]="variant(d.status)" />
              </div>
              <p class="cc-small muted">
                @if (d.packageLpa) { ₹{{ d.packageLpa }} LPA · }@if (d.location) { {{ d.location }} · }{{ d.openings || 0 }} openings
              </p>
              @if (d.status === 'REJECTED_BY_ADMIN' && d.rejectionReason) {
                <p class="reason cc-small">Changes requested: {{ d.rejectionReason }}</p>
              }
            </div>
            <div class="card__actions">
              <a [routerLink]="['/recruiter/drives', d.id]"><app-button size="sm" variant="secondary">Open</app-button></a>
              @if (editable(d.status)) {
                <app-button size="sm" [loading]="actingId() === d.id" (click)="submit(d)">Submit</app-button>
              }
              <app-button size="sm" variant="danger" (click)="askCancel(d)">Cancel</app-button>
            </div>
          </li>
        }
      </ul>
    }

    <app-modal [(open)]="confirmOpen" title="Cancel drive">
      <p class="cc-body">Cancel this drive? This can't be undone.</p>
      <div footer>
        <app-button variant="ghost" (click)="confirmOpen.set(false)">Keep drive</app-button>
        <app-button variant="danger" [loading]="actingId() !== null" (click)="cancel()">Cancel drive</app-button>
      </div>
    </app-modal>
  `,
  styles: [
    `
      .head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: var(--cc-space-6);
      }
      .head h1 {
        margin: 0;
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
        padding: var(--cc-space-5);
      }
      .card__top {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
      }
      .card__actions {
        display: flex;
        gap: var(--cc-space-2);
      }
      .muted {
        color: var(--cc-color-text-secondary);
        margin: var(--cc-space-1) 0 0;
      }
      .reason {
        margin: var(--cc-space-1) 0 0;
        color: var(--cc-color-danger);
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
        margin: 0 0 var(--cc-space-2);
        color: var(--cc-color-text-secondary);
        max-width: 380px;
      }
      .link-plain {
        color: var(--cc-color-text);
        text-decoration: none;
      }
      .link-plain:hover {
        color: var(--cc-color-primary);
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
export class DrivesListPage {
  private readonly driveSvc = inject(DriveService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly drives = signal<DriveResponse[]>([]);
  readonly actingId = signal<string | null>(null);
  readonly confirmOpen = signal(false);
  private readonly toCancel = signal<DriveResponse | null>(null);

  constructor() {
    void this.reload();
  }

  label = driveStatusLabel;
  variant(s: DriveResponse['status']) {
    return statusToVariant(s);
  }
  editable = isDriveEditable;

  async reload(): Promise<void> {
    this.state.set('loading');
    try {
      this.drives.set(await this.driveSvc.list());
      this.state.set('ready');
    } catch (e) {
      this.state.set('error');
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not load your drives.');
    }
  }

  async submit(d: DriveResponse): Promise<void> {
    this.actingId.set(d.id);
    try {
      const updated = await this.driveSvc.submit(d.id);
      this.drives.update((list) => list.map((x) => (x.id === d.id ? updated : x)));
      this.toast.success('Drive submitted for approval.');
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not submit the drive.');
    } finally {
      this.actingId.set(null);
    }
  }

  askCancel(d: DriveResponse): void {
    this.toCancel.set(d);
    this.confirmOpen.set(true);
  }
  async cancel(): Promise<void> {
    const d = this.toCancel();
    if (!d) {
      return;
    }
    this.actingId.set(d.id);
    try {
      const updated = await this.driveSvc.cancel(d.id);
      this.drives.update((list) => list.map((x) => (x.id === d.id ? updated : x)));
      this.toast.success('Drive cancelled.');
      this.confirmOpen.set(false);
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not cancel the drive.');
    } finally {
      this.actingId.set(null);
    }
  }
}
