import { Component, computed, inject, signal } from '@angular/core';
import {
  Button,
  EligibilityPanel,
  Modal,
  SegmentedSections,
  StatusPill,
  ToastService,
  statusToVariant,
} from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { DriveService } from '../student.services';
import { EligibilityGroup, StudentDrive } from '../student.models';
import { driveSections, eligibilityChecks, firstFailedReason } from '../student.mappers';

const GROUP_LABEL: Record<EligibilityGroup, string> = {
  ELIGIBLE: 'Eligible',
  APPLIED: 'Applied',
  NOT_ELIGIBLE: 'Not eligible',
  CLOSED: 'Closed',
};

/**
 * Drive discovery (Story 9.4, the hero) — the flat drive list grouped client-side into the four
 * segmented sections, each card's affordance driven by eligibility, a detail modal with the eligibility
 * panel, and optimistic apply (moves the card to Applied, reverts on failure).
 */
@Component({
  selector: 'app-student-drives',
  standalone: true,
  imports: [SegmentedSections, StatusPill, EligibilityPanel, Button, Modal],
  template: `
    <h1 class="cc-h2">Drives</h1>

    @if (state() === 'loading') {
      <p class="cc-body">Loading drives…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load drives. <button class="link" type="button" (click)="reload()">Try again</button></p>
    } @else {
      <app-segmented-sections [sections]="sections()" [(activeKey)]="activeKey">
        @if (visible().length === 0) {
          <p class="empty cc-body" role="status">{{ emptyCopy() }}</p>
        } @else {
          <div class="grid">
            @for (d of visible(); track d.id) {
              <article class="card">
                <button class="card__main" type="button" (click)="openDetail(d)">
                  <div class="card__top">
                    <span class="cc-h3">{{ d.companyName }}</span>
                    <app-status-pill [label]="groupLabel(d.group)" [variant]="variant(d.group)" />
                  </div>
                  <p class="cc-body muted">{{ d.role }}@if (d.packageLpa) { · ₹{{ d.packageLpa }} LPA }@if (d.location) { · {{ d.location }} }</p>
                  @if (d.applyDeadline) {
                    <p class="cc-small muted">Apply by {{ deadline(d.applyDeadline) }}</p>
                  }
                  @if (firstReason(d); as reason) {
                    <p class="reason cc-small">{{ reason }}</p>
                  }
                </button>
                @if (d.group === 'ELIGIBLE') {
                  <app-button size="sm" [loading]="applyingId() === d.id" (click)="apply(d)">Apply</app-button>
                }
              </article>
            }
          </div>
        }
      </app-segmented-sections>
    }

    <app-modal [(open)]="detailOpen" [title]="selected()?.companyName ?? 'Drive'">
      @if (selected(); as d) {
        <p class="cc-body muted">{{ d.role }}@if (d.packageLpa) { · ₹{{ d.packageLpa }} LPA }@if (d.location) { · {{ d.location }} }</p>
        @if (d.applyDeadline) {
          <p class="cc-small muted">Apply by {{ deadline(d.applyDeadline) }}</p>
        }
        @if (checks().length > 0) {
          <app-eligibility-panel [checks]="checks()" />
        } @else if (d.group === 'APPLIED') {
          <p class="cc-body" role="status">You've already applied to this drive.</p>
        } @else {
          <p class="cc-body muted" role="status">This drive is closed.</p>
        }
      }
      <div footer>
        @if (selected()?.group === 'ELIGIBLE') {
          <app-button [loading]="applyingId() === selected()?.id" (click)="apply(selected()!)">Apply</app-button>
        }
      </div>
    </app-modal>
  `,
  styles: [
    `
      h1 {
        margin: 0 0 var(--cc-space-6);
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        gap: var(--cc-gutter);
        margin-top: var(--cc-space-6);
      }
      .card {
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
        padding: var(--cc-space-5);
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-3);
        align-items: flex-start;
      }
      .card__main {
        all: unset;
        cursor: pointer;
        width: 100%;
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-2);
      }
      .card__top {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-2);
      }
      .muted {
        color: var(--cc-color-text-secondary);
        margin: 0;
      }
      .reason {
        margin: 0;
        color: var(--cc-color-danger);
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
export class DrivesPage {
  private readonly driveSvc = inject(DriveService);
  private readonly toast = inject(ToastService);

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly drives = signal<StudentDrive[]>([]);
  readonly activeKey = signal<string>('ELIGIBLE');
  readonly detailOpen = signal(false);
  readonly selected = signal<StudentDrive | null>(null);
  readonly applyingId = signal<string | null>(null);

  readonly sections = computed(() => driveSections(this.drives()));
  readonly visible = computed(() => this.drives().filter((d) => d.group === this.activeKey()));
  readonly checks = computed(() => (this.selected() ? eligibilityChecks(this.selected()!) : []));

  constructor() {
    void this.reload();
  }

  groupLabel(g: EligibilityGroup): string {
    return GROUP_LABEL[g];
  }
  variant(g: EligibilityGroup) {
    return statusToVariant(g);
  }
  firstReason(d: StudentDrive): string | null {
    return firstFailedReason(d);
  }
  emptyCopy(): string {
    return this.activeKey() === 'ELIGIBLE'
      ? 'No eligible drives yet — once your profile is approved, eligible drives appear here.'
      : 'Nothing here yet.';
  }

  openDetail(d: StudentDrive): void {
    this.selected.set(d);
    this.detailOpen.set(true);
  }

  async reload(): Promise<void> {
    this.state.set('loading');
    try {
      this.drives.set(await this.driveSvc.listDrives());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  deadline(iso: string): string {
    return new Date(iso).toLocaleDateString();
  }

  /** Optimistic apply: move the card to Applied immediately, revert if the server rejects. */
  async apply(drive: StudentDrive): Promise<void> {
    if (this.applyingId()) {
      return; // an apply is already in flight — ignore re-entrant card/modal clicks
    }
    this.applyingId.set(drive.id);
    this.patchGroup(drive.id, 'APPLIED');
    try {
      await this.driveSvc.apply(drive.id);
      this.toast.success('Application submitted.');
      this.detailOpen.set(false);
    } catch (e) {
      this.patchGroup(drive.id, drive.group); // revert
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not apply to this drive.');
    } finally {
      this.applyingId.set(null);
    }
  }

  private patchGroup(id: string, group: EligibilityGroup): void {
    this.drives.update((list) => list.map((d) => (d.id === id ? { ...d, group } : d)));
    this.selected.update((s) => (s && s.id === id ? { ...s, group } : s));
  }
}
