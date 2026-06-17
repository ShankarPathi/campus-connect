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
 * Forgot-password screen (Story 9.3). Requests a reset OTP for the portal+college+email, then advances to
 * the reset screen carrying those values (the code arrives by email). Anti-enumeration is preserved: the
 * result is the same whether or not the account exists — we never reveal it.
 */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, PortalToggle, Button, TextField],
  template: `
    <app-auth-layout title="Reset your password" subtitle="We'll email you a 6-digit code.">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <app-portal-toggle [(value)]="portal" />

        @if (formError()) {
          <p class="form-error" role="alert">{{ formError() }}</p>
        }

        <app-text-field label="College code" formControlName="collegeCode" autocomplete="organization" [required]="true" [error]="err('collegeCode', 'College code')" />
        <app-text-field label="Email" type="email" formControlName="email" autocomplete="email" [required]="true" [error]="err('email', 'Email')" />

        <app-button type="submit" [loading]="submitting()">Send reset code</app-button>
      </form>

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
export class ForgotPassword {
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
  });

  constructor() {
    const p = this.route.snapshot.queryParamMap.get('portal');
    if (p === 'student' || p === 'recruiter' || p === 'admin') {
      this.portal.set(p);
    }
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
    if (this.form.invalid) {
      this.focusFirstError();
      return;
    }
    this.submitting.set(true);
    const collegeCode = this.form.getRawValue().collegeCode.trim();
    const email = this.form.getRawValue().email.trim();
    try {
      await this.auth.forgotPassword(this.portal(), { collegeCode, email });
      // Anti-enumeration: always advance, carrying the values + a flag so reset shows the same message.
      await this.router.navigate(['/reset-password'], {
        queryParams: { portal: this.portal(), collegeCode, email, sent: '1' },
      });
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
