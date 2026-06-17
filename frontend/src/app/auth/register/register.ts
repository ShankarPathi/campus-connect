import { Component, ElementRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button, TextField } from '../../shared/ui';
import { AuthService } from '../../core/auth/auth.service';
import { Portal, RegisterRecruiterRequest, RegisterStudentRequest } from '../../core/auth/auth.models';
import { toAuthErrorView } from '../../core/auth/auth.errors';
import { AuthLayout } from '../auth-layout/auth-layout';
import { PortalToggle } from '../portal-toggle/portal-toggle';
import { describeControlError } from '../field-errors';

/**
 * Register screen (Story 9.3) — student or recruiter self-registration (Admin excluded: admins are
 * bootstrapped). Recruiter adds company fields. On success the form is replaced by a portal-correct
 * "check your email" coaching panel. Errors map to plain language (UX-DR12).
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, PortalToggle, Button, TextField],
  template: `
    <app-auth-layout title="Create your account" subtitle="Join your college on Campus Connect.">
      @if (done()) {
        <div class="done" aria-live="polite">
          <p class="cc-body">We sent a verification link to <strong>{{ done()!.email }}</strong>.</p>
          @if (done()!.portal === 'recruiter') {
            <p class="cc-body">Verify it, then your account waits for College Admin approval.</p>
          } @else {
            <p class="cc-body">Verify it, then sign in.</p>
          }
        </div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <app-portal-toggle [(value)]="portal" [allowed]="['student', 'recruiter']" />

          @if (formError()) {
            <p class="form-error" role="alert">{{ formError() }}</p>
          }

          <app-text-field label="College code" formControlName="collegeCode" autocomplete="organization" [required]="true" [error]="err('collegeCode', 'College code')" />
          <app-text-field label="Email" type="email" formControlName="email" autocomplete="email" [required]="true" [error]="err('email', 'Email')" />
          <app-text-field label="Password" type="password" formControlName="password" autocomplete="new-password" hint="At least 8 characters." [required]="true" [error]="err('password', 'Password')" />
          <app-text-field label="Confirm password" type="password" formControlName="confirmPassword" autocomplete="new-password" [required]="true" [error]="confirmError()" />

          @if (portal() === 'recruiter') {
            <app-text-field label="Company name" formControlName="companyName" [required]="true" [error]="err('companyName', 'Company name')" />
            <app-text-field label="Company website" formControlName="companyWebsite" type="text" [error]="err('companyWebsite', 'Company website')" />
            <app-text-field label="Industry" formControlName="industry" [error]="err('industry', 'Industry')" />
            <app-text-field label="Your designation" formControlName="recruiterDesignation" [error]="err('recruiterDesignation', 'Designation')" />
            <app-text-field label="Contact phone" type="tel" formControlName="contactPhone" [error]="err('contactPhone', 'Contact phone')" />
            <app-text-field label="Company description" formControlName="companyDescription" [error]="err('companyDescription', 'Company description')" />
          }

          <app-button type="submit" [loading]="submitting()">Create account</app-button>
        </form>
      }

      <div footer class="links">
        <span>Already have an account? <a [routerLink]="['/login']" [queryParams]="{ portal: portal() }">Sign in</a></span>
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
      .done {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-3);
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
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly portal = signal<Portal>('student');
  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly done = signal<{ email: string; portal: Portal } | null>(null);
  private readonly serverFields = signal<Record<string, string>>({});
  private readonly submitted = signal(false);

  readonly form = this.fb.nonNullable.group({
    collegeCode: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: ['', Validators.required],
    companyName: [''],
    companyWebsite: [''],
    industry: [''],
    recruiterDesignation: [''],
    contactPhone: [''],
    companyDescription: [''],
  });

  private readonly mismatch = computed(() => {
    const { password, confirmPassword } = this.form.getRawValue();
    return !!confirmPassword && password !== confirmPassword;
  });

  constructor() {
    // Register excludes Admin; only seed a self-registerable portal carried in from the login link.
    const p = this.route.snapshot.queryParamMap.get('portal');
    if (p === 'student' || p === 'recruiter') {
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

  confirmError(): string {
    const control = this.form.get('confirmPassword');
    if (this.serverFields()['confirmPassword']) {
      return this.serverFields()['confirmPassword'];
    }
    if ((this.submitted() || control?.touched) && this.mismatch()) {
      return 'Passwords do not match.';
    }
    return this.err('confirmPassword', 'Confirm password');
  }

  async submit(): Promise<void> {
    this.submitted.set(true);
    this.formError.set(null);
    this.serverFields.set({});

    const v = this.form.getRawValue();
    this.form.patchValue(
      { collegeCode: v.collegeCode.trim(), email: v.email.trim(), companyName: v.companyName.trim() },
      { emitEvent: false },
    );

    const recruiter = this.portal() === 'recruiter';
    const companyName = this.form.controls.companyName;
    companyName.setValidators(recruiter ? [Validators.required] : []);
    companyName.updateValueAndValidity({ emitEvent: false });

    if (this.form.invalid || this.mismatch()) {
      this.focusFirstError();
      return;
    }

    this.submitting.set(true);
    try {
      await this.auth.register(this.portal(), this.buildBody());
      this.done.set({ email: this.form.getRawValue().email, portal: this.portal() });
    } catch (e) {
      const view = toAuthErrorView(e);
      this.formError.set(view.formMessage);
      this.serverFields.set(view.fieldErrors);
    } finally {
      this.submitting.set(false);
    }
  }

  private buildBody(): RegisterStudentRequest | RegisterRecruiterRequest {
    const v = this.form.getRawValue();
    const base = { collegeCode: v.collegeCode, email: v.email, password: v.password };
    if (this.portal() !== 'recruiter') {
      return base;
    }
    const optional = (s: string) => (s.trim() ? s.trim() : undefined);
    return {
      ...base,
      companyName: v.companyName.trim(),
      companyWebsite: optional(v.companyWebsite),
      industry: optional(v.industry),
      recruiterDesignation: optional(v.recruiterDesignation),
      contactPhone: optional(v.contactPhone),
      companyDescription: optional(v.companyDescription),
    };
  }

  private focusFirstError(): void {
    queueMicrotask(() => {
      const el = this.host.nativeElement.querySelector('.field__input--error') as HTMLInputElement | null;
      el?.focus();
    });
  }
}
