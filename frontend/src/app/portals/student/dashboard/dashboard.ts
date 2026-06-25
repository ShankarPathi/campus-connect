import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProgressRing, StatusPill, StatusVariant, statusToVariant } from '../../../shared/ui';
import { ApplicationsService, DriveService, OffersService, ProfileService } from '../student.services';
import { ApplicationStatus } from '../student.models';

const TERMINAL: ApplicationStatus[] = ['REJECTED', 'WITHDRAWN', 'OFFER_DECLINED', 'OFFER_EXPIRED'];

/** The student's placement journey, in order — the dashboard highlights how far they've progressed. */
const JOURNEY = ['Profile', 'Applied', 'Shortlisted', 'Interview', 'Offer', 'Placed'];

interface DeadlineRow {
  company: string;
  role: string;
  soon: boolean;
  label: string;
}
interface ActivityRow {
  company: string;
  role: string;
  variant: StatusVariant;
  label: string;
}

/** "OFFER_RELEASED" → "Offer released" — humanise a backend enum for display. */
function humanizeStatus(s: string): string {
  const t = s.toLowerCase().replace(/_/g, ' ');
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/**
 * Student dashboard (Story 9.4) — a light landing composed from the existing list endpoints (no new API):
 * the profile-completion ring + clickable stat tiles (eligible drives / active applications / offers).
 */
@Component({
  selector: 'app-student-dashboard',
  standalone: true,
  imports: [RouterLink, ProgressRing, StatusPill],
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

      <section class="card journey">
        <h2 class="cc-body-medium jtitle">Your placement journey</h2>
        <ol class="steps">
          @for (s of journey; track s; let i = $index) {
            <li class="step" [class.step--done]="i < journeyStep()" [class.step--now]="i === journeyStep()">
              <span class="step__dot">{{ i < journeyStep() ? '✓' : i + 1 }}</span>
              <span class="step__label cc-small">{{ s }}</span>
            </li>
          }
        </ol>
      </section>

      <div class="cols">
        <section class="card col">
          <h2 class="cc-body-medium ctitle">Upcoming deadlines</h2>
          @for (d of deadlines(); track $index) {
            <a class="row" routerLink="/student/drives">
              <span class="row__main">
                <span class="cc-body-medium">{{ d.company }}</span>
                <span class="cc-small muted">{{ d.role }}</span>
              </span>
              <span class="row__meta cc-small" [class.row__meta--soon]="d.soon">{{ d.label }}</span>
            </a>
          } @empty {
            <p class="cc-small muted rowempty">No upcoming deadlines right now.</p>
          }
        </section>

        <section class="card col">
          <h2 class="cc-body-medium ctitle">Recent activity</h2>
          @for (a of activity(); track $index) {
            <div class="row">
              <span class="row__main">
                <span class="cc-body-medium">{{ a.company }}</span>
                <span class="cc-small muted">{{ a.role }}</span>
              </span>
              <app-status-pill [variant]="a.variant" [label]="a.label" />
            </div>
          } @empty {
            <p class="cc-small muted rowempty">No activity yet — apply to a drive to get started.</p>
          }
        </section>
      </div>

      @if (coach(); as c) {
        <a class="coach" [routerLink]="c.link" role="status">
          <span class="coach__icon" aria-hidden="true">{{ c.icon }}</span>
          <span class="coach__text">
            <span class="cc-body-medium">{{ c.title }}</span>
            <span class="cc-small coach__sub">{{ c.sub }}</span>
          </span>
          <span class="coach__cta cc-small">{{ c.cta }} →</span>
        </a>
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
      .journey {
        margin-top: var(--cc-gutter);
        padding: var(--cc-space-5) var(--cc-space-6);
      }
      .jtitle,
      .ctitle {
        margin: 0 0 var(--cc-space-4);
      }
      .steps {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        gap: var(--cc-space-2);
      }
      .step {
        position: relative;
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--cc-space-2);
        text-align: center;
      }
      /* connector line between dots */
      .step:not(:last-child)::after {
        content: '';
        position: absolute;
        top: 13px;
        left: 50%;
        width: 100%;
        height: 2px;
        background: var(--cc-color-border);
        z-index: 0;
      }
      .step--done:not(:last-child)::after {
        background: var(--cc-color-primary);
      }
      .step__dot {
        position: relative;
        z-index: 1;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        border-radius: var(--cc-radius-full);
        background: var(--cc-color-surface);
        border: 2px solid var(--cc-color-border);
        color: var(--cc-color-text-secondary);
        font-size: 12px;
        font-weight: 700;
      }
      .step--done .step__dot {
        background: var(--cc-color-primary);
        border-color: var(--cc-color-primary);
        color: #fff;
      }
      .step--now .step__dot {
        border-color: var(--cc-color-primary);
        color: var(--cc-color-primary);
        box-shadow: 0 0 0 4px var(--cc-color-primary-subtle);
      }
      .step--done .step__label,
      .step--now .step__label {
        color: var(--cc-color-text);
        font-weight: 600;
      }
      .cols {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--cc-gutter);
        margin-top: var(--cc-gutter);
      }
      .col {
        padding: var(--cc-space-5) var(--cc-space-6);
      }
      .row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-3);
        padding: var(--cc-space-3) 0;
        border-bottom: 1px solid var(--cc-color-border);
        text-decoration: none;
        color: var(--cc-color-text);
      }
      .row:last-child {
        border-bottom: none;
      }
      a.row:hover .row__main > :first-child {
        color: var(--cc-color-primary);
      }
      .row__main {
        display: flex;
        flex-direction: column;
        gap: 1px;
        min-width: 0;
      }
      .row__meta {
        color: var(--cc-color-text-secondary);
        white-space: nowrap;
      }
      .row__meta--soon {
        color: var(--cc-color-danger);
        font-weight: 600;
      }
      .rowempty {
        padding: var(--cc-space-3) 0;
      }
      @media (max-width: 760px) {
        .cols {
          grid-template-columns: 1fr;
        }
        .step__label {
          font-size: 10px;
        }
      }
      .skeleton {
        height: 96px;
        background: var(--cc-color-surface);
      }
      .coach {
        display: flex;
        align-items: center;
        gap: var(--cc-space-4);
        margin-top: var(--cc-space-6);
        padding: var(--cc-space-4) var(--cc-space-5);
        background: var(--cc-portal-soft, var(--cc-color-primary-subtle));
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        text-decoration: none;
        color: var(--cc-color-text);
        transition:
          box-shadow 0.12s ease,
          transform 0.12s ease;
      }
      .coach:hover {
        box-shadow: var(--cc-shadow-md, var(--cc-shadow-sm));
        transform: translateY(-1px);
      }
      .coach__icon {
        font-size: 28px;
        line-height: 1;
        flex: none;
      }
      .coach__text {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .coach__sub {
        color: var(--cc-color-text-secondary);
      }
      .coach__cta {
        margin-left: auto;
        color: var(--cc-color-primary);
        font-weight: 600;
        white-space: nowrap;
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

  protected readonly journey = JOURNEY;

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly completion = signal(0);
  readonly eligible = signal(0);
  readonly activeApplications = signal(0);
  readonly offers = signal(0);
  readonly deadlines = signal<DeadlineRow[]>([]);
  readonly activity = signal<ActivityRow[]>([]);
  /** 0=Profile … 5=Placed — how far the student has progressed through the journey strip. */
  readonly journeyStep = signal(0);

  /**
   * The single most useful next step for this student, shown as a coaching card. Profile completion comes
   * first (it gates eligibility); then a "no eligible drives yet" nudge so the empty tile is never a dead end.
   */
  readonly coach = computed(() => {
    if (this.completion() < 100) {
      return {
        icon: '📝',
        title: 'Complete your profile',
        sub: 'Eligible drives unlock once your profile is approved.',
        cta: 'Finish profile',
        link: '/student/profile',
      };
    }
    if (this.eligible() === 0) {
      return {
        icon: '🔍',
        title: 'No eligible drives yet',
        sub: "They'll appear here as recruiters post drives you match.",
        cta: 'Browse all drives',
        link: '/student/drives',
      };
    }
    return null;
  });

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

      const now = Date.now();
      this.deadlines.set(
        drives
          .filter((d) => d.group === 'ELIGIBLE' && d.applyDeadline && Date.parse(d.applyDeadline) > now)
          .sort((a, b) => Date.parse(a.applyDeadline!) - Date.parse(b.applyDeadline!))
          .slice(0, 4)
          .map((d) => {
            const days = Math.ceil((Date.parse(d.applyDeadline!) - now) / 86_400_000);
            const label = days <= 0 ? 'Closes today' : days === 1 ? 'Closes tomorrow' : `Closes in ${days} days`;
            return { company: d.companyName, role: d.role, soon: days <= 3, label };
          }),
      );

      this.activity.set(
        [...applications]
          .sort((a, b) => (Date.parse(b.appliedAt) || 0) - (Date.parse(a.appliedAt) || 0))
          .slice(0, 4)
          .map((a) => ({
            company: a.companyName ?? 'A drive',
            role: a.role ?? '—',
            variant: statusToVariant(a.status),
            label: humanizeStatus(a.status),
          })),
      );

      const hasStatus = (...s: ApplicationStatus[]) => applications.some((a) => s.includes(a.status));
      this.journeyStep.set(
        profile.isPlaced
          ? 5
          : offers.length || hasStatus('OFFER_RELEASED', 'OFFER_ACCEPTED')
            ? 4
            : hasStatus('INTERVIEWING', 'SELECTED')
              ? 3
              : hasStatus('SHORTLISTED')
                ? 2
                : applications.length
                  ? 1
                  : 0,
      );

      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }
}
