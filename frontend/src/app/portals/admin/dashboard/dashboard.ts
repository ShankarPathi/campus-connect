import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardService } from '../admin.services';
import { DashboardSnapshot } from '../admin.models';

/**
 * Admin dashboard (Story 9.6) — the TPO season snapshot. Clickable pending-action tiles route to their
 * approval queue; season-count tiles summarize the season. Matches the approved admin-dashboard mockup.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="hero">
      <div>
        <h1 class="hero__t">Season overview</h1>
        <p class="hero__s">Everything across your placement drive, at a glance.</p>
      </div>
      <span class="hero__e" aria-hidden="true">🛡️</span>
    </section>

    @if (state() === 'loading') {
      <div class="grid"><div class="card sk"></div><div class="card sk"></div><div class="card sk"></div></div>
    } @else if (state() === 'error') {
      <div class="card"><p class="cc-body">We couldn't load your dashboard. <button class="link" type="button" (click)="reload()">Try again</button></p></div>
    } @else if (snapshot(); as s) {
      <h2 class="cc-h3 section">Pending actions</h2>
      <div class="grid">
        <a class="card tile tile--action tone-amber tile-tint" routerLink="/admin/students">
          <span class="stat-chip">📝</span>
          <span class="cc-display stat-num">{{ s.pendingProfileApprovals }}</span>
          <span class="cc-small muted">Profiles to review</span>
        </a>
        <a class="card tile tile--action tone-sky tile-tint" routerLink="/admin/recruiters">
          <span class="stat-chip">🏢</span>
          <span class="cc-display stat-num">{{ s.pendingRecruiterApprovals }}</span>
          <span class="cc-small muted">Recruiters to approve</span>
        </a>
        <a class="card tile tile--action tone-blue tile-tint" routerLink="/admin/drives">
          <span class="stat-chip">📋</span>
          <span class="cc-display stat-num">{{ s.pendingDriveApprovals }}</span>
          <span class="cc-small muted">Drives to approve</span>
        </a>
      </div>

      <h2 class="cc-h3 section">This season</h2>
      <div class="grid">
        <div class="card tile tone-purple tile-tint"><span class="stat-chip">🎓</span><span class="cc-display stat-num">{{ s.totalStudents }}</span><span class="cc-small muted">Students</span></div>
        <div class="card tile tone-blue tile-tint"><span class="stat-chip">📋</span><span class="cc-display stat-num">{{ s.totalDrives }}</span><span class="cc-small muted">Drives</span></div>
        <div class="card tile tone-amber tile-tint"><span class="stat-chip">📄</span><span class="cc-display stat-num">{{ s.totalApplications }}</span><span class="cc-small muted">Applications</span></div>
        <a class="card tile tile--action tone-green tile-tint" routerLink="/admin/placements"><span class="stat-chip">🏆</span><span class="cc-display stat-num">{{ s.placedStudents }}</span><span class="cc-small muted">Placed</span></a>
      </div>
    }
  `,
  styles: [
    `
      .hero {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-4);
        background: var(--cc-portal-grad, var(--cc-color-primary));
        color: #fff;
        border-radius: var(--cc-radius-lg);
        padding: var(--cc-space-6) var(--cc-space-8);
        margin-bottom: var(--cc-space-6);
        box-shadow: var(--cc-shadow-sm);
      }
      .hero__t {
        font: var(--cc-text-h1);
        margin: 0;
      }
      .hero__s {
        margin: var(--cc-space-1) 0 0;
        opacity: 0.92;
      }
      .hero__e {
        font-size: 46px;
        line-height: 1;
      }
      .section {
        margin: var(--cc-space-6) 0 var(--cc-space-3);
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
        gap: var(--cc-gutter);
      }
      .card {
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
        padding: var(--cc-space-6);
        text-decoration: none;
        color: var(--cc-color-text);
      }
      .tile {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-1);
      }
      .tile--action:hover {
        border-color: var(--cc-color-primary);
      }
      .sk {
        height: 96px;
        background: var(--cc-color-surface);
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
export class AdminDashboardPage {
  private readonly svc = inject(DashboardService);
  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly snapshot = signal<DashboardSnapshot | null>(null);

  constructor() {
    void this.reload();
  }

  async reload(): Promise<void> {
    this.state.set('loading');
    try {
      this.snapshot.set(await this.svc.snapshot());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }
}
