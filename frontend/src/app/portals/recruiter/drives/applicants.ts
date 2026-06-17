import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { ApplicantService } from '../recruiter.services';
import { ApplicantSummary, ApplicationStatus, ApplicantQuery, PageResponse } from '../recruiter.models';
import { applicantStatusLabel } from '../recruiter.mappers';

const FILTERS: ApplicationStatus[] = ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEWING', 'SELECTED', 'REJECTED'];
const PAGE_SIZE = 20;

/**
 * Applicants tab (Story 9.5, UX-DR7 hero) — the server-paged applicant table with a status filter + search,
 * sortable headers, pagination, row selection + a contextual bulk-action bar (shortlist / reject / select).
 * All filtering/sorting/paging is server-driven (the query goes to the API; we render the returned page).
 */
@Component({
  selector: 'app-recruiter-applicants',
  standalone: true,
  imports: [FormsModule, StatusPill, Button, Modal],
  template: `
    <div class="bar">
      <input
        class="search"
        type="search"
        placeholder="Search name or roll number"
        [(ngModel)]="searchText"
        (keydown.enter)="applySearch()"
        aria-label="Search applicants"
      />
      <div class="filters" role="group" aria-label="Filter by status">
        @for (s of filters; track s) {
          <button
            type="button"
            class="chip"
            [class.chip--on]="isStatusOn(s)"
            [attr.aria-pressed]="isStatusOn(s)"
            (click)="toggleStatus(s)"
          >
            {{ label(s) }}
          </button>
        }
      </div>
    </div>

    @if (selected().size > 0) {
      <div class="bulkbar" role="region" aria-label="Bulk actions">
        <span class="cc-body-medium">{{ selected().size }} selected</span>
        <app-button size="sm" [loading]="busy()" (click)="shortlist()">Shortlist</app-button>
        <app-button size="sm" variant="secondary" [loading]="busy()" (click)="ask('select')">Select</app-button>
        <app-button size="sm" variant="danger" [loading]="busy()" (click)="ask('reject')">Reject</app-button>
        <app-button size="sm" variant="ghost" (click)="clearSelection()">Clear</app-button>
      </div>
    }

    @if (state() === 'loading') {
      <p class="cc-body">Loading applicants…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load applicants. <button class="link" type="button" (click)="load()">Try again</button></p>
    } @else if (rows().length === 0) {
      <p class="empty cc-body" role="status">No applicants match — adjust the filters or check back once students apply.</p>
    } @else {
      <table class="table">
        <thead>
          <tr>
            <th class="th th--check">
              <input type="checkbox" [checked]="allSelected()" (change)="toggleAll($event)" aria-label="Select all on this page" />
            </th>
            <th class="th th--sortable" role="button" tabindex="0" (click)="sort('fullName')" (keydown.enter)="sort('fullName')">Name {{ sortArrow('fullName') }}</th>
            <th class="th">Branch / Batch</th>
            <th class="th th--sortable" role="button" tabindex="0" (click)="sort('cgpa')" (keydown.enter)="sort('cgpa')">CGPA {{ sortArrow('cgpa') }}</th>
            <th class="th">Backlogs</th>
            <th class="th">Status</th>
            <th class="th th--right">Résumé</th>
          </tr>
        </thead>
        <tbody>
          @for (a of rows(); track a.applicationId) {
            <tr class="tr" [class.tr--sel]="selected().has(a.applicationId)">
              <td class="td td--check">
                <input type="checkbox" [checked]="selected().has(a.applicationId)" (change)="toggleOne(a.applicationId)" [attr.aria-label]="'Select ' + a.fullName" />
              </td>
              <td class="td"><span class="cc-body-medium">{{ a.fullName }}</span><br /><span class="cc-small muted">{{ a.rollNumber }}</span></td>
              <td class="td">{{ a.branch }}<span class="muted"> · {{ a.batch }}</span></td>
              <td class="td">{{ a.cgpa }}</td>
              <td class="td">{{ a.activeBacklogs }}</td>
              <td class="td"><app-status-pill [label]="label(a.status)" [variant]="variant(a.status)" /></td>
              <td class="td td--right"><button class="link" type="button" (click)="openResume(a.applicationId)">View</button></td>
            </tr>
          }
        </tbody>
      </table>

      <div class="pager">
        <app-button size="sm" variant="secondary" [disabled]="page() <= 0" (click)="goto(page() - 1)">Previous</app-button>
        <span class="cc-small muted">Page {{ page() + 1 }} of {{ Math.max(totalPages(), 1) }}</span>
        <app-button size="sm" variant="secondary" [disabled]="page() + 1 >= totalPages()" (click)="goto(page() + 1)">Next</app-button>
      </div>
    }

    <app-modal [(open)]="confirmOpen" [title]="confirmKind() === 'select' ? 'Select applicants' : 'Reject applicants'">
      <p class="cc-body">
        {{ confirmKind() === 'select'
          ? 'Mark the ' + selected().size + ' selected applicant(s) as final selections?'
          : 'Reject the ' + selected().size + ' selected applicant(s)? They will be notified.' }}
      </p>
      <div footer>
        <app-button variant="ghost" (click)="confirmOpen.set(false)">Cancel</app-button>
        <app-button [variant]="confirmKind() === 'reject' ? 'danger' : 'primary'" [loading]="busy()" (click)="runConfirm()">
          {{ confirmKind() === 'select' ? 'Select' : 'Reject' }}
        </app-button>
      </div>
    </app-modal>
  `,
  styles: [
    `
      .bar {
        display: flex;
        flex-wrap: wrap;
        gap: var(--cc-space-3);
        align-items: center;
        margin-bottom: var(--cc-space-4);
      }
      .search {
        font: var(--cc-text-body);
        border: 1px solid var(--cc-color-border-strong);
        border-radius: var(--cc-radius-sm);
        padding: var(--cc-space-2) var(--cc-space-3);
        min-width: 240px;
      }
      .filters {
        display: flex;
        flex-wrap: wrap;
        gap: var(--cc-space-2);
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
      .bulkbar {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
        padding: var(--cc-space-3) var(--cc-space-4);
        margin-bottom: var(--cc-space-3);
        background: var(--cc-color-primary-subtle);
        border-radius: var(--cc-radius-md);
      }
      .table {
        width: 100%;
        border-collapse: collapse;
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
      }
      .th {
        font: var(--cc-text-caption);
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--cc-color-text-secondary);
        text-align: left;
        padding: var(--cc-space-3) var(--cc-space-4);
        border-bottom: 1px solid var(--cc-color-border);
      }
      .th--right,
      .td--right {
        text-align: right;
      }
      .th--check,
      .td--check {
        width: 36px;
      }
      .th--sortable {
        cursor: pointer;
      }
      .td {
        font: var(--cc-text-body);
        padding: var(--cc-space-3) var(--cc-space-4);
        border-bottom: 1px solid var(--cc-color-border);
        vertical-align: top;
      }
      .tr--sel {
        background: var(--cc-color-primary-subtle);
      }
      .muted {
        color: var(--cc-color-text-secondary);
      }
      .empty {
        margin-top: var(--cc-space-8);
        color: var(--cc-color-text-secondary);
      }
      .pager {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: var(--cc-space-3);
        margin-top: var(--cc-space-4);
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
export class RecruiterApplicants {
  private readonly svc = inject(ApplicantService);
  private readonly toast = inject(ToastService);
  protected readonly Math = Math;
  protected readonly filters = FILTERS;

  readonly driveId = input.required<string>();

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly rows = signal<ApplicantSummary[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly selected = signal<Set<string>>(new Set());
  readonly busy = signal(false);
  readonly confirmOpen = signal(false);
  readonly confirmKind = signal<'select' | 'reject'>('reject');

  searchText = '';
  private statusFilter = signal<ApplicationStatus[]>([]);
  private sortBy = signal<string | undefined>(undefined);
  private sortDir = signal<'asc' | 'desc'>('asc');

  readonly allSelected = computed(() => this.rows().length > 0 && this.rows().every((r) => this.selected().has(r.applicationId)));

  constructor() {
    queueMicrotask(() => this.load());
  }

  label(s: ApplicationStatus): string {
    return applicantStatusLabel(s);
  }
  variant(s: ApplicationStatus) {
    return statusToVariant(s);
  }
  isStatusOn(s: ApplicationStatus): boolean {
    return this.statusFilter().includes(s);
  }
  sortArrow(key: string): string {
    return this.sortBy() === key ? (this.sortDir() === 'asc' ? '▲' : '▼') : '';
  }

  private query(): ApplicantQuery {
    return {
      status: this.statusFilter().length ? this.statusFilter() : undefined,
      search: this.searchText || undefined,
      sortBy: this.sortBy(),
      sortDir: this.sortBy() ? this.sortDir() : undefined,
      page: this.page(),
      pageSize: PAGE_SIZE,
    };
  }

  async load(): Promise<void> {
    this.state.set('loading');
    try {
      const res: PageResponse<ApplicantSummary> = await this.svc.list(this.driveId(), this.query());
      this.rows.set(res.items);
      this.totalPages.set(res.totalPages);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  // Any query change resets to page 0 AND clears the selection — a selected id from a different filter/page
  // must never reach a bulk POST.
  applySearch(): void {
    this.page.set(0);
    this.clearSelection();
    void this.load();
  }
  toggleStatus(s: ApplicationStatus): void {
    this.statusFilter.update((list) => (list.includes(s) ? list.filter((x) => x !== s) : [...list, s]));
    this.page.set(0);
    this.clearSelection();
    void this.load();
  }
  sort(key: string): void {
    if (this.sortBy() === key) {
      this.sortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortBy.set(key);
      this.sortDir.set('asc');
    }
    this.page.set(0);
    this.clearSelection();
    void this.load();
  }
  goto(p: number): void {
    this.page.set(p);
    this.clearSelection();
    void this.load();
  }

  toggleOne(id: string): void {
    this.selected.update((set) => {
      const next = new Set(set);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }
  toggleAll(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.selected.set(checked ? new Set(this.rows().map((r) => r.applicationId)) : new Set());
  }
  clearSelection(): void {
    this.selected.set(new Set());
  }

  async openResume(applicationId: string): Promise<void> {
    // Open the tab synchronously (within the click gesture) so it isn't popup-blocked; set its URL once
    // the fresh presigned link resolves. The link is never cached.
    const win = window.open('', '_blank');
    if (win) {
      win.opener = null;
    }
    try {
      const { url } = await this.svc.resumeUrl(this.driveId(), applicationId);
      if (win) {
        win.location.href = url;
      } else {
        window.open(url, '_blank', 'noopener');
      }
    } catch (e) {
      win?.close();
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not open the résumé.');
    }
  }

  async shortlist(): Promise<void> {
    await this.runBulk(() => this.svc.shortlist(this.driveId(), [...this.selected()]), 'Shortlisted.');
  }
  ask(kind: 'select' | 'reject'): void {
    this.confirmKind.set(kind);
    this.confirmOpen.set(true);
  }
  async runConfirm(): Promise<void> {
    if (this.confirmKind() === 'reject') {
      await this.runBulk(() => this.svc.reject(this.driveId(), [...this.selected()]), 'Rejected.');
    } else {
      await this.runBulk(() => this.svc.select(this.driveId(), [...this.selected()]), 'Selected.');
    }
    this.confirmOpen.set(false);
  }

  private async runBulk(action: () => Promise<{ failedCount: number; warning?: string | null }>, okMsg: string): Promise<void> {
    this.busy.set(true);
    try {
      const res = await action();
      // One outcome message: failures, else the soft warning (advisory), else success.
      if (res.failedCount > 0) {
        this.toast.error(`${res.failedCount} could not be updated.`);
      } else if (res.warning) {
        this.toast.error(res.warning);
      } else {
        this.toast.success(okMsg);
      }
      this.clearSelection();
      await this.load();
      // Clamp off the now-empty last page so the user isn't stranded.
      if (this.rows().length === 0 && this.page() > 0) {
        this.page.set(Math.max(0, this.totalPages() - 1));
        await this.load();
      }
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not complete that action.');
    } finally {
      this.busy.set(false);
    }
  }
}
