import { Component, computed, input } from '@angular/core';

/** Floating role-themed chips behind the card — switch with the selected portal. */
const DECO: Record<string, string[]> = {
  student: ['🎓', '📚', '✏️', '🎒', '💡', '📅'],
  recruiter: ['💼', '🏢', '📈', '🤝', '📋', '⭐'],
  admin: ['🛡️', '📊', '⚙️', '🏆', '📁', '✅'],
  '': ['🎓', '💼', '🛡️', '📈', '🤝', '✨'],
};

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
 * AuthLayout (Story 9.3, +premium redesign) — a split-screen auth page on a live, portal-themed animated
 * gradient with floating role chips. Left: a welcome panel whose message switches with the selected portal.
 * Right: a frosted-glass form card (projected body + footer). The optional `portal` input drives the theme,
 * the welcome copy, and `--cc-color-primary` (so the form's button/links match the chosen portal).
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
        <span class="blob blob--a"></span>
        <span class="blob blob--b"></span>
        @for (d of deco(); track $index) {
          <span class="deco deco--{{ $index + 1 }}">{{ d }}</span>
        }
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
      /* animated, portal-themed gradient (idea #3) */
      .auth {
        position: relative;
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: var(--cc-space-8) var(--cc-space-4);
        overflow: hidden;
        background: linear-gradient(125deg, #3730a3, #6366f1, #818cf8, #6366f1);
        background-size: 300% 300%;
        animation: gradientShift 18s ease infinite;
      }
      .auth.portal--student {
        background: linear-gradient(125deg, #1e3a8a, #2563eb, #60a5fa, #3b82f6);
        background-size: 300% 300%;
      }
      .auth.portal--recruiter {
        background: linear-gradient(125deg, #065f46, #059669, #34d399, #10b981);
        background-size: 300% 300%;
      }
      .auth.portal--admin {
        background: linear-gradient(125deg, #5b21b6, #7c3aed, #a78bfa, #8b5cf6);
        background-size: 300% 300%;
      }
      @keyframes gradientShift {
        0% {
          background-position: 0% 50%;
        }
        50% {
          background-position: 100% 50%;
        }
        100% {
          background-position: 0% 50%;
        }
      }

      /* depth + floating glass chips */
      .auth__bg {
        position: absolute;
        inset: 0;
        pointer-events: none;
        overflow: hidden;
      }
      .blob {
        position: absolute;
        border-radius: 50%;
        filter: blur(64px);
      }
      .blob--a {
        width: 460px;
        height: 460px;
        background: rgba(255, 255, 255, 0.3);
        top: -150px;
        left: -120px;
      }
      .blob--b {
        width: 380px;
        height: 380px;
        background: rgba(255, 255, 255, 0.16);
        bottom: -140px;
        right: -90px;
      }
      .deco {
        position: absolute;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 22px;
        background: rgba(255, 255, 255, 0.18);
        border: 1px solid rgba(255, 255, 255, 0.35);
        backdrop-filter: blur(7px);
        -webkit-backdrop-filter: blur(7px);
        box-shadow: 0 12px 28px rgba(16, 24, 40, 0.18);
        user-select: none;
        line-height: 1;
        animation: float 7s ease-in-out infinite;
      }
      .deco--1 {
        top: 9%;
        left: 7%;
        width: 84px;
        height: 84px;
        font-size: 40px;
        animation-delay: 0s;
      }
      .deco--2 {
        top: 16%;
        right: 9%;
        width: 64px;
        height: 64px;
        font-size: 30px;
        animation-delay: 1.3s;
      }
      .deco--3 {
        bottom: 14%;
        left: 6%;
        width: 72px;
        height: 72px;
        font-size: 34px;
        animation-delay: 0.6s;
      }
      .deco--4 {
        bottom: 9%;
        right: 7%;
        width: 92px;
        height: 92px;
        font-size: 44px;
        animation-delay: 2s;
      }
      .deco--5 {
        top: 50%;
        left: 4%;
        width: 56px;
        height: 56px;
        font-size: 26px;
        animation-delay: 2.6s;
      }
      .deco--6 {
        top: 62%;
        right: 4%;
        width: 60px;
        height: 60px;
        font-size: 28px;
        animation-delay: 0.9s;
      }
      @keyframes float {
        0%,
        100% {
          transform: translateY(0);
        }
        50% {
          transform: translateY(-14px);
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
  readonly deco = computed(() => DECO[this.portal()] ?? DECO['']);
  readonly welcome = computed(() => WELCOME[this.portal()] ?? WELCOME['']);
}
