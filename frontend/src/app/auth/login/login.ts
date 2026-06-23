import { Component, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Button, TextField } from '../../shared/ui';
import { AuthService } from '../../core/auth/auth.service';
import { Portal } from '../../core/auth/auth.models';
import { toAuthErrorView } from '../../core/auth/auth.errors';
import { AuthLayout } from '../auth-layout/auth-layout';
import { PortalToggle } from '../portal-toggle/portal-toggle';
import { describeControlError } from '../field-errors';

/**
 * Login screen (Story 9.3). Portal segmented control + collegeCode/email/password against the per-portal
 * login endpoint. On success → the `returnUrl` from the 9.2 auth guard, else the portal home. Errors are
 * mapped to plain language (UX-DR12); the "Create account" link is hidden for Admin (no self-register).
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, PortalToggle, Button, TextField],
  template: `
    <app-auth-layout title="Sign in" subtitle="Welcome back to CampusConnect.">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <app-portal-toggle [(value)]="portal" />

        @if (formError()) {
          <p class="form-error" role="alert">{{ formError() }}</p>
        }

        <app-text-field
          label="College code"
          formControlName="collegeCode"
          autocomplete="organization"
          [required]="true"
          [error]="err('collegeCode', 'College code')"
        />
        <app-text-field
          label="Email"
          type="email"
          formControlName="email"
          autocomplete="username"
          [required]="true"
          [error]="err('email', 'Email')"
        />
        <app-text-field
          label="Password"
          type="password"
          formControlName="password"
          autocomplete="current-password"
          [required]="true"
          [error]="err('password', 'Password')"
        />

        <app-button type="submit" [loading]="submitting()">Sign in</app-button>
      </form>

      <div footer class="links">
        @if (portal() !== 'admin') {
          <a [routerLink]="['/register']" [queryParams]="{ portal: portal() }">Create an account</a>
        }
        <a [routerLink]="['/forgot-password']" [queryParams]="{ portal: portal() }">Forgot password?</a>
      </div>
    </app-auth-layout>
  `,
  styles: [
    `
      form {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
      }
      .form-error {
        margin: 0;
        padding: var(--cc-space-3);
        font: var(--cc-text-small);
        color: var(--cc-color-danger);
        background: var(--cc-color-danger-subtle);
        border-radius: var(--cc-radius-sm);
      }
      .links {
        display: flex;
        justify-content: space-between;
        gap: var(--cc-space-4);
      }
      .links a {
        color: var(--cc-color-primary);
        text-decoration: none;
      }
      .links a:hover {
        text-decoration: underline;
      }
    `,
  ],
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly portal = signal<Portal>('student');
  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  private readonly serverFields = signal<Record<string, string>>({});
  private readonly submitted = signal(false);

  readonly form = this.fb.nonNullable.group({
    collegeCode: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  constructor() {
    const p = this.route.snapshot.queryParamMap.get('portal');
    if (p === 'student' || p === 'recruiter' || p === 'admin') {
      this.portal.set(p);
    }
    // Clear stale server-side field errors once the user edits (they're only re-derived on the next submit).
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (Object.keys(this.serverFields()).length) {
        this.serverFields.set({});
      }
    });
  }

  err(name: string, label: string): string {
    const server = this.serverFields()[name];
    if (server) {
      return server;
    }
    const control = this.form.get(name);
    if (control && (this.submitted() || control.touched) && control.invalid) {
      return describeControlError(control.errors, label) ?? '';
    }
    return '';
  }

  async submit(): Promise<void> {
    this.submitted.set(true);
    this.formError.set(null);
    this.serverFields.set({});
    this.trimIdentityFields();
    if (this.form.invalid) {
      this.focusFirstError();
      return;
    }
    this.submitting.set(true);
    try {
      await this.auth.login(this.portal(), this.form.getRawValue());
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
      await this.router.navigateByUrl(returnUrl || `/${this.portal()}`);
    } catch (e) {
      const view = toAuthErrorView(e);
      this.formError.set(view.formMessage);
      this.serverFields.set(view.fieldErrors);
    } finally {
      this.submitting.set(false);
    }
  }

  /** Trim the identity fields so a pasted/autofilled leading/trailing space doesn't fail validation. */
  private trimIdentityFields(): void {
    const v = this.form.getRawValue();
    this.form.patchValue({ collegeCode: v.collegeCode.trim(), email: v.email.trim() }, { emitEvent: false });
  }

  private focusFirstError(): void {
    queueMicrotask(() => {
      const el = this.host.nativeElement.querySelector('.field__input--error') as HTMLInputElement | null;
      el?.focus();
    });
  }
}
