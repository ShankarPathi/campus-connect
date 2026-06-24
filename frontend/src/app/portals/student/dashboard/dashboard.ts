import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProgressRing } from '../../../shared/ui';
import { ApplicationsService, DriveService, OffersService, ProfileService } from '../student.services';
import { ApplicationStatus } from '../student.models';

const TERMINAL: ApplicationStatus[] = ['REJECTED', 'WITHDRAWN', 'OFFER_DECLINED', 'OFFER_EXPIRED'];

/**
 * Student dashboard (Story 9.4) — a light landing composed from the existing list endpoints (no new API):
 * the profile-completion ring + clickable stat tiles (eligible drives / active applications / offers).
 */
@Component({
  selector: 'app-student-dashboard',
  standalone: true,
  imports: [RouterLink, ProgressRing],
  template: `
    <section class="hero">
      <div>
        <h1 class="hero__t">Welcome back 👋</h1>
        <p class="hero__s">Here's your placement journey.</p>
      </div>
      <span class="hero__e" aria-hidden="true">🎓</span>
    </section>

    @if (state() === 'loading') {
      <div class="grid">
        <div class="card skeleton"></div>
        <div class="card skeleton"></div>
        <div class="card skeleton"></div>
        <div class="card skeleton"></div>
      </div>
    } @else if (state() === 'error') {
      <div class="card">
        <p class="cc-body">We couldn't load your dashboard.</p>
        <button class="retry cc-body-medium" type="button" (click)="reload()">Try again</button>
      </div>
    } @else {
      <div class="grid">
        <a class="card ring-card" routerLink="/student/profile">
          <app-progress-ring [percent]="completion()" />
          <div>
            <p class="cc-body-medium">Profile</p>
            <p class="cc-small muted">{{ completion() }}% complete</p>
          </div>
        </a>
        <a class="card tile tone-blue tile-tint" routerLink="/student/drives">
          <span class="stat-chip">🎓</span>
          <span class="cc-display stat-num">{{ eligible() }}</span>
          <span class="cc-small muted">Eligible drives</span>
        </a>
        <a class="card tile tone-amber tile-tint" routerLink="/student/applications">
          <span class="stat-chip">📄</span>
          <span class="cc-display stat-num">{{ activeApplications() }}</span>
          <span class="cc-small muted">Active applications</span>
        </a>
        <a class="card tile tone-green tile-tint" routerLink="/student/offers">
          <span class="stat-chip">🎁</span>
          <span class="cc-display stat-num">{{ offers() }}</span>
          <span class="cc-small muted">Offers</span>
        </a>
      </div>

      @if (completion() < 100) {
        <p class="coach cc-body" role="status">Complete your profile so eligible drives appear here.</p>
      }
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
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
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
      a.card:hover {
        border-color: var(--cc-color-border-strong);
      }
      .ring-card {
        display: flex;
        align-items: center;
        gap: var(--cc-space-4);
      }
      .tile {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-1);
      }
      .muted {
        color: var(--cc-color-text-secondary);
      }
      .skeleton {
        height: 96px;
        background: var(--cc-color-surface);
      }
      .coach {
        margin-top: var(--cc-space-6);
        color: var(--cc-color-text-secondary);
      }
      .retry {
        margin-top: var(--cc-space-3);
        border: 1px solid var(--cc-color-border-strong);
        background: var(--cc-color-surface-raised);
        color: var(--cc-color-text);
        border-radius: var(--cc-radius-sm);
        padding: var(--cc-space-1) var(--cc-space-3);
        cursor: pointer;
      }
    `,
  ],
})
export class DashboardPage {
  private readonly profileSvc = inject(ProfileService);
  private readonly driveSvc = inject(DriveService);
  private readonly applicationsSvc = inject(ApplicationsService);
  private readonly offersSvc = inject(OffersService);

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly completion = signal(0);
  readonly eligible = signal(0);
  readonly activeApplications = signal(0);
  readonly offers = signal(0);

  constructor() {
    void this.reload();
  }

  async reload(): Promise<void> {
    this.state.set('loading');
    try {
      const [profile, drives, applications, offers] = await Promise.all([
        this.profileSvc.getProfile(),
        this.driveSvc.listDrives(),
        this.applicationsSvc.listApplications(),
        this.offersSvc.listOffers(),
      ]);
      this.completion.set(profile.completionPercent ?? 0);
      this.eligible.set(drives.filter((d) => d.group === 'ELIGIBLE').length);
      this.activeApplications.set(applications.filter((a) => !TERMINAL.includes(a.status)).length);
      this.offers.set(offers.length);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }
}
