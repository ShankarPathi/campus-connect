import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from '../../shared/ui';
import { AuthService } from '../../core/auth/auth.service';
import { Portal } from '../../core/auth/auth.models';
import { AuthLayout } from '../auth-layout/auth-layout';

type VerifyState = 'loading' | 'success' | 'invalid' | 'error' | 'missing';

/**
 * Email-verification screen (Story 9.3; state-model hardening Story 9.7). Reads `token` + `portal` from the
 * query string and confirms the emailed token via GET. States: verifying / verified / invalid-or-used (only a
 * genuine EMAIL_VERIFY_TOKEN_INVALID / 400) / transient-error-with-retry (network, 429, 5xx) / missing-params.
 * Only student and recruiter verify (admin has no verification). The token is held only in component state for
 * retry — never logged or stored.
 */
@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [RouterLink, AuthLayout, Button],
  template: `
    <app-auth-layout title="Verify your email">
      <div aria-live="polite">
        @switch (state()) {
          @case ('loading') {
            <p class="cc-body">Verifying your email…</p>
          }
          @case ('success') {
            <p class="cc-body">Your email is verified — you can sign in now.</p>
          }
          @case ('invalid') {
            <p class="cc-body">This verification link is invalid or has already been used.</p>
          }
          @case ('error') {
            <p class="cc-body">We couldn't verify your email right now. Please check your connection and try again.</p>
          }
          @case ('missing') {
            <p class="cc-body">Open the verification link from your email to continue.</p>
          }
        }
      </div>

      <div footer class="links">
        @if (state() === 'success') {
          <a [routerLink]="['/login']" [queryParams]="{ portal: portal }"><app-button>Sign in</app-button></a>
        } @else if (state() === 'error') {
          <app-button (click)="retry()">Try again</app-button>
          <a [routerLink]="['/login']">Back to sign in</a>
        } @else if (state() === 'invalid' || state() === 'missing') {
          <a [routerLink]="['/login']">Back to sign in</a>
        }
      </div>
    </app-auth-layout>
  `,
  styles: [
    `
      .links {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
      }
      .links a {
        color: var(--cc-color-primary);
        text-decoration: none;
      }
    `,
  ],
})
export class VerifyEmail {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  readonly state = signal<VerifyState>('loading');
  portal: Portal = 'student';
  private token = '';

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const token = params.get('token');
    const portal = params.get('portal');
    if (!token || (portal !== 'student' && portal !== 'recruiter')) {
      this.state.set('missing');
      return;
    }
    this.portal = portal;
    this.token = token;
    this.verify();
  }

  /** Re-run verification after a transient failure (the token is still held in component state). */
  retry(): void {
    this.state.set('loading');
    this.verify();
  }

  private verify(): void {
    this.auth
      .verifyEmail(this.portal, this.token)
      .then(() => this.state.set('success'))
      .catch((err: unknown) => this.state.set(this.isInvalidToken(err) ? 'invalid' : 'error'));
  }

  /** A genuinely bad/used token (EMAIL_VERIFY_TOKEN_INVALID or a 400) vs a transient failure (network/429/5xx). */
  private isInvalidToken(err: unknown): boolean {
    const e = err as { code?: string; status?: number; error?: { error?: { code?: string } } };
    const code = e?.code ?? e?.error?.error?.code;
    if (code) {
      return code === 'EMAIL_VERIFY_TOKEN_INVALID';
    }
    return e?.status === 400; // a 400 with no parseable code is still a bad-request about the token
  }
}
