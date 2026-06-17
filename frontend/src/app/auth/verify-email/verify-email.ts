import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from '../../shared/ui';
import { AuthService } from '../../core/auth/auth.service';
import { Portal } from '../../core/auth/auth.models';
import { AuthLayout } from '../auth-layout/auth-layout';

type VerifyState = 'loading' | 'success' | 'invalid' | 'missing';

/**
 * Email-verification screen (Story 9.3). Reads `token` + `portal` from the query string and confirms the
 * emailed token via GET. States: verifying / verified / invalid-or-used / missing-params. Only student and
 * recruiter verify (admin has no verification). The token is used transiently — never logged or stored.
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
          @case ('missing') {
            <p class="cc-body">Open the verification link from your email to continue.</p>
          }
        }
      </div>

      <div footer class="links">
        @if (state() === 'success') {
          <a [routerLink]="['/login']" [queryParams]="{ portal: portal }"><app-button>Sign in</app-button></a>
        } @else if (state() === 'invalid' || state() === 'missing') {
          <a [routerLink]="['/login']">Back to sign in</a>
        }
      </div>
    </app-auth-layout>
  `,
  styles: [
    `
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

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const token = params.get('token');
    const portal = params.get('portal');
    if (!token || (portal !== 'student' && portal !== 'recruiter')) {
      this.state.set('missing');
      return;
    }
    this.portal = portal;
    this.auth
      .verifyEmail(portal, token)
      .then(() => this.state.set('success'))
      .catch(() => this.state.set('invalid'));
  }
}
