import { Component, ElementRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button, TextField } from '../../shared/ui';
import { AuthService } from '../../core/auth/auth.service';
import { Portal } from '../../core/auth/auth.models';
import { toAuthErrorView } from '../../core/auth/auth.errors';
import { AuthLayout } from '../auth-layout/auth-layout';
import { PortalToggle } from '../portal-toggle/portal-toggle';
import { describeControlError } from '../field-errors';

/**
 * Reset-password screen (Story 9.3). Sets a new password using the emailed 6-digit OTP. Prefills
 * portal/college/email from the forgot step. On success it shows a confirmation that routes to sign in.
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, PortalToggle, Button, TextField],
  template: `
    <app-auth-layout title="Set a new password">
      @if (done()) {
        <div class="done" aria-live="polite">
          <p class="cc-body">Your password is updated — sign in with your new password.</p>
          <a [routerLink]="['/login']" [queryParams]="{ portal: portal() }"><app-button>Sign in</app-button></a>
        </div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <app-portal-toggle [(value)]="portal" />

          @if (sent()) {
            <p class="notice" aria-live="polite">If an account exists, we've sent a 6-digit code to your email.</p>
          }
          @if (formError()) {
            <p class="form-error" role="alert">{{ formError() }}</p>
          }

          <app-text-field label="College code" formControlName="collegeCode" autocomplete="organization" [required]="true" [error]="err('collegeCode', 'College code')" />
          <app-text-field label="Email" type="email" formControlName="email" autocomplete="email" [required]="true" [error]="err('email', 'Email')" />
          <app-text-field label="6-digit code" formControlName="otp" inputmode="numeric" autocomplete="one-time-code" [required]="true" [error]="err('otp', 'Code')" />
          <app-text-field label="New password" type="password" formControlName="newPassword" autocomplete="new-password" hint="At least 8 characters." [required]="true" [error]="err('newPassword', 'New password')" />
          <app-text-field label="Confirm new password" type="password" formControlName="confirmPassword" autocomplete="new-password" [required]="true" [error]="confirmError()" />

          <app-button type="submit" [loading]="submitting()">Update password</app-button>
        </form>
      }

      <div footer class="links">
        <a [routerLink]="['/login']" [queryParams]="{ portal: portal() }">Back to sign in</a>
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
      .notice {
        margin: 0;
        padding: var(--cc-space-3);
        font: var(--cc-text-small);
        color: var(--cc-color-text-secondary);
        background: var(--cc-color-surface);
        border-radius: var(--cc-radius-sm);
      }
      .form-error {
        margin: 0;
        padding: var(--cc-space-3);
        font: var(--cc-text-small);
        color: var(--cc-color-danger);
        background: var(--cc-color-danger-subtle);
        border-radius: var(--cc-radius-sm);
      }
      .links a {
        color: var(--cc-color-primary);
        text-decoration: none;
      }
    `,
  ],
})
export class ResetPassword {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly portal = signal<Portal>('student');
  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly done = signal(false);
  readonly sent = signal(false);
  private readonly serverFields = signal<Record<string, string>>({});
  private readonly submitted = signal(false);

  readonly form = this.fb.nonNullable.group({
    collegeCode: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: ['', Validators.required],
  });

  private readonly mismatch = computed(() => {
    const { newPassword, confirmPassword } = this.form.getRawValue();
    return !!confirmPassword && newPassword !== confirmPassword;
  });

  constructor() {
    const q = this.route.snapshot.queryParamMap;
    const p = q.get('portal');
    if (p === 'student' || p === 'recruiter' || p === 'admin') {
      this.portal.set(p);
    }
    this.form.patchValue(
      { collegeCode: q.get('collegeCode') ?? '', email: q.get('email') ?? '' },
      { emitEvent: false },
    );
    this.sent.set(q.get('sent') === '1');
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

  confirmError(): string {
    const control = this.form.get('confirmPassword');
    if ((this.submitted() || control?.touched) && this.mismatch()) {
      return 'Passwords do not match.';
    }
    return this.err('confirmPassword', 'Confirm new password');
  }

  async submit(): Promise<void> {
    this.submitted.set(true);
    this.formError.set(null);
    this.serverFields.set({});
    const raw = this.form.getRawValue();
    this.form.patchValue(
      { collegeCode: raw.collegeCode.trim(), email: raw.email.trim(), otp: raw.otp.trim() },
      { emitEvent: false },
    );
    if (this.form.invalid || this.mismatch()) {
      this.focusFirstError();
      return;
    }
    this.submitting.set(true);
    const { collegeCode, email, otp, newPassword } = this.form.getRawValue();
    try {
      await this.auth.resetPassword(this.portal(), { collegeCode, email, otp, newPassword });
      this.done.set(true);
    } catch (e) {
      const view = toAuthErrorView(e);
      this.formError.set(view.formMessage);
      this.serverFields.set(view.fieldErrors);
    } finally {
      this.submitting.set(false);
    }
  }

  private focusFirstError(): void {
    queueMicrotask(() => {
      const el = this.host.nativeElement.querySelector('.field__input--error') as HTMLInputElement | null;
      el?.focus();
    });
  }
}
