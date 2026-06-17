import { Component } from '@angular/core';

/** Placeholder login page (Story 9.2) — the real authentication screens land in Story 9.3. */
@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login">
      <h1 class="cc-h1">Campus Connect</h1>
      <p class="cc-body">Sign in — authentication screens arrive in Story 9.3.</p>
    </div>
  `,
  styles: [
    `
      .login {
        max-width: 360px;
        margin: 10vh auto;
        padding: var(--cc-space-8);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
        text-align: center;
      }
    `,
  ],
})
export class Login {}
