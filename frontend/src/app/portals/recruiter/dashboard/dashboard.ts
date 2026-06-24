import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '../../../shared/ui';
import { DriveService } from '../recruiter.services';
import { DriveCounts, driveCounts } from '../recruiter.mappers';

/**
 * Recruiter dashboard (Story 9.5) — a light landing composed from the my-drives list (no recruiter-dashboard
 * API): drive-status stat tiles + a create-a-drive action.
 */
@Component({
  selector: 'app-recruiter-dashboard',
  standalone: true,
  imports: [RouterLink, Button],
  template: `
    <section class="hero">
      <div>
        <h1 class="hero__t">Your hiring at a glance</h1>
        <p class="hero__s">Track your drives and candidates.</p>
      </div>
      <a routerLink="/recruiter/drives/new"><app-button variant="secondary">+ Create a drive</app-button></a>
    </section>

    @if (state() === 'loading') {
      <div class="grid"><div class="card sk"></div><div class="card sk"></div><div class="card sk"></div><div class="card sk"></div></div>
    } @else if (state() === 'error') {
      <div class="card"><p class="cc-body">We couldn't load your dashboard. <button class="link" type="button" (click)="reload()">Try again</button></p></div>
    } @else if (counts().total === 0) {
      <div class="card"><p class="cc-body">Create your first drive to start hiring.</p></div>
    } @else {
      <div class="grid">
        <a class="card tile tone-amber tile-tint" routerLink="/recruiter/drives"><span class="stat-chip">📝</span><span class="cc-display stat-num">{{ counts().drafts }}</span><span class="cc-small muted">Drafts</span></a>
        <a class="card tile tone-sky tile-tint" routerLink="/recruiter/drives"><span class="stat-chip">⏳</span><span class="cc-display stat-num">{{ counts().pending }}</span><span class="cc-small muted">Pending approval</span></a>
        <a class="card tile tone-green tile-tint" routerLink="/recruiter/drives"><span class="stat-chip">📢</span><span class="cc-display stat-num">{{ counts().open }}</span><span class="cc-small muted">Open</span></a>
        <a class="card tile tone-purple tile-tint" routerLink="/recruiter/drives"><span class="stat-chip">💼</span><span class="cc-display stat-num">{{ counts().total }}</span><span class="cc-small muted">Total drives</span></a>
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
      a.card:hover {
        border-color: var(--cc-color-border-strong);
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
export class RecruiterDashboardPage {
  private readonly driveSvc = inject(DriveService);

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly counts = signal<DriveCounts>({ drafts: 0, pending: 0, open: 0, total: 0 });

  constructor() {
    void this.reload();
  }

  async reload(): Promise<void> {
    this.state.set('loading');
    try {
      this.counts.set(driveCounts(await this.driveSvc.list()));
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }
}
