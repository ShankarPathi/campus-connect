import { Component, computed, input } from '@angular/core';

/** Per-portal welcome panel content (idea #4). */
const WELCOME: Record<string, { emoji: string; line: string; points: string[] }> = {
  student: {
    emoji: '🎓',
    line: 'Find your dream placement',
    points: ['Discover drives you’re eligible for', 'Apply in a single click', 'Track every interview & offer'],
  },
  recruiter: {
    emoji: '💼',
    line: 'Hire top campus talent',
    points: ['Post and manage drives', 'Shortlist applicants with ease', 'Run interviews end-to-end'],
  },
  admin: {
    emoji: '🛡️',
    line: 'Run your placement season',
    points: ['Approve students & drives', 'Track placements live', 'Export accreditation reports'],
  },
  '': {
    emoji: '✨',
    line: 'Connecting Talent with Opportunity',
    points: ['One campus placement platform', 'For students, recruiters & admins', 'From application to placement'],
  },
};

/**
 * AuthLayout (Story 9.3, +premium redesign) — a split-screen auth page on a rich, portal-themed mesh
 * gradient with a faint dot grid and softly drifting glass orbs. Left: a welcome panel whose message
 * switches with the selected portal. Right: a frosted-glass form card (projected body + footer). The
 * optional `portal` input drives the theme, the welcome copy, and `--cc-color-primary` (so the form's
 * button/links match the chosen portal).
 */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  template: `
    <main
      class="auth"
      [class.portal--student]="portal() === 'student'"
      [class.portal--recruiter]="portal() === 'recruiter'"
      [class.portal--admin]="portal() === 'admin'"
    >
      <div class="auth__bg" aria-hidden="true">
        <span class="grid"></span>
        <span class="orb orb--a"></span>
        <span class="orb orb--b"></span>
        <span class="orb orb--c"></span>
      </div>

      <section class="card">
        <aside class="welcome" aria-hidden="true">
          <span class="welcome__emoji">{{ welcome().emoji }}</span>
          <h2 class="welcome__line">{{ welcome().line }}</h2>
          <p class="welcome__tag">Connecting Talent with Opportunity</p>
          <ul class="welcome__points">
            @for (p of welcome().points; track p) {
              <li><span class="welcome__check">✓</span>{{ p }}</li>
            }
          </ul>
        </aside>

        <div class="form" role="region" [attr.aria-label]="title()">
          <div class="brand">
            <img class="brand__logo" src="icon.svg" alt="" width="40" height="40" />
            <span class="brand__text">
              <span class="brand__name">CampusConnect</span>
              <span class="brand__tag">Connecting Talent with Opportunity</span>
            </span>
          </div>
          <h1 class="cc-h1 form__title">{{ title() }}</h1>
          @if (subtitle()) {
            <p class="cc-body form__sub">{{ subtitle() }}</p>
          }
          <div class="form__body"><ng-content /></div>
          <div class="form__footer"><ng-content select="[footer]" /></div>
        </div>
      </section>
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      /* Rich multi-hue mesh gradient per portal — depth, not a flat single colour. */
      .auth {
        position: relative;
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: var(--cc-space-8) var(--cc-space-4);
        overflow: hidden;
        background-color: #312e81;
        background-image:
          radial-gradient(at 16% 20%, #6366f1 0px, transparent 50%),
          radial-gradient(at 84% 14%, #4f46e5 0px, transparent 48%),
          radial-gradient(at 78% 86%, #7c3aed 0px, transparent 50%),
          radial-gradient(at 20% 82%, #4338ca 0px, transparent 48%);
      }
      .auth.portal--student {
        background-color: #1e3a8a;
        background-image:
          radial-gradient(at 16% 20%, #3b82f6 0px, transparent 50%),
          radial-gradient(at 84% 14%, #2563eb 0px, transparent 48%),
          radial-gradient(at 78% 86%, #0ea5e9 0px, transparent 50%),
          radial-gradient(at 20% 82%, #1e40af 0px, transparent 48%);
      }
      .auth.portal--recruiter {
        background-color: #064e3b;
        background-image:
          radial-gradient(at 16% 20%, #10b981 0px, transparent 50%),
          radial-gradient(at 84% 14%, #059669 0px, transparent 48%),
          radial-gradient(at 78% 86%, #14b8a6 0px, transparent 50%),
          radial-gradient(at 20% 82%, #047857 0px, transparent 48%);
      }
      .auth.portal--admin {
        background-color: #4c1d95;
        background-image:
          radial-gradient(at 16% 20%, #8b5cf6 0px, transparent 50%),
          radial-gradient(at 84% 14%, #7c3aed 0px, transparent 48%),
          radial-gradient(at 78% 86%, #a855f7 0px, transparent 50%),
          radial-gradient(at 20% 82%, #6d28d9 0px, transparent 48%);
      }
      /* depth: a faint dot grid + softly drifting glass orbs (calmer than floating icons) */
      .auth__bg {
        position: absolute;
        inset: 0;
        pointer-events: none;
        overflow: hidden;
      }
      .grid {
        position: absolute;
        inset: 0;
        background-image: radial-gradient(rgba(255, 255, 255, 0.16) 1px, transparent 1px);
        background-size: 26px 26px;
        mask-image: radial-gradient(circle at 50% 40%, #000 0%, transparent 75%);
        -webkit-mask-image: radial-gradient(circle at 50% 40%, #000 0%, transparent 75%);
        opacity: 0.5;
      }
      .orb {
        position: absolute;
        border-radius: 50%;
        filter: blur(58px);
        will-change: transform;
      }
      .orb--a {
        width: 440px;
        height: 440px;
        background: rgba(255, 255, 255, 0.34);
        top: -150px;
        left: -110px;
        animation: drift 16s ease-in-out infinite;
      }
      .orb--b {
        width: 360px;
        height: 360px;
        background: rgba(255, 255, 255, 0.18);
        bottom: -130px;
        right: -90px;
        animation: drift 20s ease-in-out infinite reverse;
      }
      .orb--c {
        width: 280px;
        height: 280px;
        background: rgba(255, 255, 255, 0.12);
        top: 38%;
        left: 60%;
        animation: drift 24s ease-in-out infinite;
        animation-delay: 3s;
      }
      @keyframes drift {
        0%,
        100% {
          transform: translate(0, 0);
        }
        50% {
          transform: translate(30px, -28px);
        }
      }

      /* the split card */
      .card {
        position: relative;
        z-index: 1;
        display: flex;
        width: 100%;
        max-width: 900px;
        border-radius: var(--cc-radius-lg);
        overflow: hidden;
        box-shadow: 0 30px 70px -24px rgba(16, 24, 40, 0.55);
        border: 1px solid rgba(255, 255, 255, 0.35);
      }

      /* left welcome panel (idea #1 + #4) */
      .welcome {
        flex: 0 0 42%;
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: var(--cc-space-3);
        padding: var(--cc-space-10) var(--cc-space-8);
        background: var(--cc-portal-grad, linear-gradient(135deg, #4338ca, #6366f1));
        color: #fff;
      }
      .welcome__emoji {
        font-size: 56px;
        line-height: 1;
      }
      .welcome__line {
        font: var(--cc-text-h1);
        font-size: 30px;
        margin: var(--cc-space-2) 0 0;
      }
      .welcome__tag {
        margin: 0;
        opacity: 0.9;
        font: var(--cc-text-body);
      }
      .welcome__points {
        list-style: none;
        margin: var(--cc-space-4) 0 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-2);
      }
      .welcome__points li {
        display: flex;
        align-items: center;
        gap: var(--cc-space-2);
        font: var(--cc-text-small);
        opacity: 0.96;
      }
      .welcome__check {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 20px;
        height: 20px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.22);
        font-size: 11px;
        flex: none;
      }

      /* right frosted-glass form panel */
      .form {
        flex: 1;
        background: rgba(255, 255, 255, 0.9);
        backdrop-filter: blur(22px) saturate(150%);
        -webkit-backdrop-filter: blur(22px) saturate(150%);
        padding: var(--cc-space-8);
      }
      .brand {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
        margin-bottom: var(--cc-space-5);
      }
      .brand__logo {
        width: 40px;
        height: 40px;
        border-radius: var(--cc-radius-md);
        flex: none;
        box-shadow: var(--cc-shadow-sm);
      }
      .brand__text {
        display: flex;
        flex-direction: column;
        line-height: 1.15;
      }
      .brand__name {
        font: var(--cc-text-h3);
        color: var(--cc-color-text);
      }
      .brand__tag {
        font: var(--cc-text-caption);
        color: var(--cc-color-text-secondary);
      }
      .form__title {
        margin: 0;
      }
      .form__sub {
        margin: var(--cc-space-2) 0 0;
        color: var(--cc-color-text-secondary);
      }
      .form__body {
        margin-top: var(--cc-space-6);
      }
      .form__footer {
        margin-top: var(--cc-space-6);
        font: var(--cc-text-small);
        color: var(--cc-color-text-secondary);
      }

      /* stack on small screens — welcome panel hides, form stays usable */
      @media (max-width: 860px) {
        .card {
          max-width: 440px;
        }
        .welcome {
          display: none;
        }
      }
    `,
  ],
})
export class AuthLayout {
  readonly title = input('');
  readonly subtitle = input('');
  /** '' | 'student' | 'recruiter' | 'admin' — themes the background, welcome copy + accent. */
  readonly portal = input<string>('');
  readonly welcome = computed(() => WELCOME[this.portal()] ?? WELCOME['']);
}
