import { Component, inject, signal } from '@angular/core';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { PlacementService } from '../admin.services';
import { PlacementRecord, PlacementStatus } from '../admin.models';
import { placementStatusLabel } from '../admin.mappers';

const FILTERS: PlacementStatus[] = ['PENDING_CONFIRMATION', 'OFFICIALLY_PLACED'];

/** Placements confirmation (Story 9.6) — list by placement status, officially confirm a pending placement. */
@Component({
  selector: 'app-admin-placements',
  standalone: true,
  imports: [Button, Modal, StatusPill],
  template: `
    <h1 class="cc-h2">Placements</h1>
    <div class="filters" role="group" aria-label="Filter by status">
      @for (f of filters; track f) {
        <button type="button" class="chip" [class.chip--on]="status() === f" [attr.aria-pressed]="status() === f" (click)="setStatus(f)">{{ label(f) }}</button>
      }
    </div>

    @if (state() === 'loading') {
      <p class="cc-body">Loading…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load placements. <button class="link" type="button" (click)="load()">Try again</button></p>
    } @else if (rows().length === 0) {
      <p class="empty cc-body" role="status">No placements to confirm.</p>
    } @else {
      <ul class="list">
        @for (p of rows(); track p.id) {
          <li class="card">
            <div>
              <span class="cc-body-medium">{{ p.company }}@if (p.role) { · {{ p.role }} }</span>
              <p class="cc-small muted">
                @if (p.ctc != null) { ₹{{ p.ctc }} LPA }
                @if (p.joiningDate) { · {{ formatDate(p.joiningDate) }} }
              </p>
            </div>
            <div class="card__actions">
              <app-status-pill [label]="label(p.status)" [variant]="variant(p.status)"></app-status-pill>
              @if (p.status === 'PENDING_CONFIRMATION') {
                <app-button size="sm" [loading]="acting()" (click)="openConfirm(p)">Confirm placement</app-button>
              }
            </div>
          </li>
        }
      </ul>
    }

    <app-modal [(open)]="confirmOpen" title="Confirm placement">
      <p class="cc-body">Officially confirm this placement? This is audited and can't be undone.</p>
      <div footer>
        <app-button variant="ghost" (click)="confirmOpen.set(false)">Cancel</app-button>
        <app-button [loading]="acting()" (click)="doConfirm()">Confirm placement</app-button>
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
        align-items: center;
        gap: var(--cc-space-2);
        flex: 0 0 auto;
      }
      .muted {
        color: var(--cc-color-text-secondary);
        margin: var(--cc-space-1) 0 0;
      }
      .empty {
        margin-top: var(--cc-space-8);
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
export class PlacementsPage {
  private readonly svc = inject(PlacementService);
  private readonly toast = inject(ToastService);
  protected readonly filters = FILTERS;

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly rows = signal<PlacementRecord[]>([]);
  readonly status = signal<PlacementStatus>('PENDING_CONFIRMATION');
  readonly acting = signal(false);
  readonly confirmOpen = signal(false);
  private readonly active = signal<PlacementRecord | null>(null);

  constructor() {
    void this.load();
  }

  label = placementStatusLabel;
  variant = statusToVariant;
  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString();
  }

  setStatus(s: PlacementStatus): void {
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
    } catch {
      if (seq !== this.loadSeq) {
        return;
      }
      this.state.set('error');
    }
  }

  openConfirm(p: PlacementRecord): void {
    this.active.set(p);
    this.confirmOpen.set(true);
  }

  async doConfirm(): Promise<void> {
    const p = this.active();
    if (!p) {
      return;
    }
    await this.run(() => this.svc.confirm(p.id), 'Placement confirmed.');
    this.confirmOpen.set(false);
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
