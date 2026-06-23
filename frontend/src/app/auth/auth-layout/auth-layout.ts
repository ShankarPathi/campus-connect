import { Component, input } from '@angular/core';

/**
 * AuthLayout (Story 9.3) — the centered card used by every public auth screen: logo, a title/subtitle,
 * the projected form body, and a projected footer-links slot. Sits on the grey `--cc-color-surface` page.
 */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  template: `
    <main class="auth">
      <section class="auth__card" role="region" [attr.aria-label]="title()">
        <div class="auth__brand">
          <img class="auth__logo" src="icon.svg" alt="" width="40" height="40" />
          <span class="auth__brandtext">
            <span class="auth__wordmark">CampusConnect</span>
            <span class="auth__tagline">Connecting Talent with Opportunity</span>
          </span>
        </div>
        <h1 class="cc-h1 auth__title">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="cc-body auth__subtitle">{{ subtitle() }}</p>
        }
        <div class="auth__body">
          <ng-content />
        </div>
        <div class="auth__footer">
          <ng-content select="[footer]" />
        </div>
      </section>
    </main>
  `,
  styles: [
    `
      .auth {
        min-height: 100vh;
        display: flex;
        align-items: flex-start;
        justify-content: center;
        padding: var(--cc-space-12) var(--cc-space-4);
        background: var(--cc-color-surface);
      }
      .auth__card {
        width: 100%;
        max-width: 420px;
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
        padding: var(--cc-space-8);
      }
      .auth__brand {
        display: flex;
        align-items: center;
        gap: var(--cc-space-2);
        margin-bottom: var(--cc-space-6);
      }
      .auth__logo {
        width: 40px;
        height: 40px;
        border-radius: var(--cc-radius-md);
        flex: none;
      }
      .auth__brandtext {
        display: flex;
        flex-direction: column;
        line-height: 1.15;
      }
      .auth__wordmark {
        font: var(--cc-text-h3);
        color: var(--cc-color-text);
      }
      .auth__tagline {
        font: var(--cc-text-caption);
        color: var(--cc-color-text-secondary);
      }
      .auth__title {
        margin: 0;
      }
      .auth__subtitle {
        margin: var(--cc-space-2) 0 0;
        color: var(--cc-color-text-secondary);
      }
      .auth__body {
        margin-top: var(--cc-space-6);
      }
      .auth__footer {
        margin-top: var(--cc-space-6);
        font: var(--cc-text-small);
        color: var(--cc-color-text-secondary);
      }
    `,
  ],
})
export class AuthLayout {
  readonly title = input('');
  readonly subtitle = input('');
}
