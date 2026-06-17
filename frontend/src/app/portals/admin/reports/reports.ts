import { Component, computed, inject, signal } from '@angular/core';
import { Button, DataTable, TableColumn, ToastService } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { AuthStore } from '../../../core/auth/auth.store';
import { ReportService } from '../admin.services';
import { PlacementReport } from '../admin.models';

const BRANCH_COLUMNS: TableColumn[] = [
  { key: 'branch', header: 'Branch' },
  { key: 'totalStudents', header: 'Total', align: 'right' },
  { key: 'placedStudents', header: 'Placed', align: 'right' },
  { key: 'placementPercent', header: 'Placed %', align: 'right' },
];

const COMPANY_COLUMNS: TableColumn[] = [
  { key: 'company', header: 'Company' },
  { key: 'placements', header: 'Placements', align: 'right' },
];

/**
 * Placement reports (Story 9.6, AC8) — the season-wide placement summary. Shows the overall headline
 * percentage, branch-wise and company-wise breakdown tables, and a CSV export that streams the report
 * file straight to a browser download. Token-styled, no hard-coded hex.
 */
@Component({
  selector: 'app-admin-reports',
  standalone: true,
  imports: [Button, DataTable],
  template: `
    <div class="head">
      <h1 class="cc-h2">Placement reports</h1>
      @if (state() === 'ready') {
        <app-button size="sm" variant="secondary" [loading]="exporting()" (click)="exportCsv()">Export CSV</app-button>
      }
    </div>

    @if (state() === 'loading') {
      <p class="cc-body">Loading…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load the reports. <button class="link" type="button" (click)="load()">Try again</button></p>
    } @else if (report(); as r) {
      <section class="card headline">
        <span class="cc-display">{{ r.overall.placementPercent }}% placed</span>
        <span class="cc-small muted">{{ r.overall.placedStudents }} of {{ r.overall.totalStudents }} students placed</span>
      </section>

      <h2 class="cc-h3 section">By branch</h2>
      <div class="card table-wrap">
        <app-data-table [columns]="branchColumns" [rows]="branchRows()" />
      </div>

      <h2 class="cc-h3 section">By company</h2>
      <div class="card table-wrap">
        <app-data-table [columns]="companyColumns" [rows]="companyRows()" />
      </div>
    }
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
      .section {
        margin: var(--cc-space-6) 0 var(--cc-space-3);
      }
      .card {
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
      }
      .headline {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-1);
        padding: var(--cc-space-6);
      }
      .table-wrap {
        overflow: hidden;
      }
      .muted {
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
export class ReportsPage {
  private readonly svc = inject(ReportService);
  private readonly toast = inject(ToastService);
  private readonly store = inject(AuthStore);

  protected readonly branchColumns = BRANCH_COLUMNS;
  protected readonly companyColumns = COMPANY_COLUMNS;

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly report = signal<PlacementReport | null>(null);
  readonly exporting = signal(false);

  readonly branchRows = computed<Record<string, unknown>[]>(() =>
    (this.report()?.branchwise ?? []).map((b) => ({
      branch: b.branch,
      totalStudents: b.totalStudents,
      placedStudents: b.placedStudents,
      placementPercent: b.placementPercent,
    })),
  );

  readonly companyRows = computed<Record<string, unknown>[]>(() =>
    (this.report()?.companywise ?? []).map((c) => ({ company: c.company, placements: c.placements })),
  );

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.state.set('loading');
    try {
      this.report.set(await this.svc.report());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  async exportCsv(): Promise<void> {
    this.exporting.set(true);
    try {
      const csv = await this.svc.exportCsv();
      const blob = new Blob([csv], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `placements-${this.store.tenantId() ?? 'export'}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      // Revoke after the download has had a chance to start (a synchronous revoke can cancel it).
      setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not export the report.');
    } finally {
      this.exporting.set(false);
    }
  }
}
