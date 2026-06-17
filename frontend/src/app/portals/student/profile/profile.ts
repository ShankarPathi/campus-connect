import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button, ProgressRing, StatusPill, TextField, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { ProfileService } from '../student.services';
import { ResumeView, StudentProfile, StudentProfileRequest } from '../student.models';
import { profileStatusLabel } from '../student.mappers';

const MAX_RESUME_MB = 5;
const MAX_RESUME_BYTES = MAX_RESUME_MB * 1024 * 1024; // matches the backend cap (app.resume.max-size-bytes:5242880)
const NUMERIC = /^\d*\.?\d+$/;

/**
 * Profile builder (Story 9.4) — a sectioned reactive form (Personal / Academic / Placement) with a
 * completion ring, save-draft + submit-for-approval, approval/rejection/locked banners, and a resume
 * upload/preview panel. A locked profile renders read-only (approval and lock are independent).
 */
@Component({
  selector: 'app-student-profile',
  standalone: true,
  imports: [ReactiveFormsModule, Button, TextField, ProgressRing, StatusPill],
  template: `
    <div class="head">
      <h1 class="cc-h2">Profile</h1>
      @if (profile(); as p) {
        <app-status-pill [label]="statusLabel(p)" [variant]="statusVariant(p)" />
      }
    </div>

    @if (state() === 'loading') {
      <p class="cc-body">Loading your profile…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load your profile. <button class="link" type="button" (click)="reload()">Try again</button></p>
    } @else {
      @if (profile()?.isLocked) {
        <p class="banner banner--lock" role="status">Your profile is locked for this placement season — it's now read-only.</p>
      }
      @if (profile()?.profileApprovalStatus === 'REJECTED' && profile()?.rejectionReason) {
        <p class="banner banner--reject" role="alert">Changes requested: {{ profile()?.rejectionReason }}</p>
      }

      <div class="layout">
        <form class="form" [formGroup]="form">
          <section id="sec-personal" class="card" formGroupName="personal">
            <h2 class="cc-h3">Personal</h2>
            <app-text-field label="Full name" formControlName="fullName" />
            <app-text-field label="Phone" type="tel" formControlName="phone" />
            <app-text-field label="Gender" formControlName="gender" />
            <app-text-field label="Date of birth" formControlName="dateOfBirth" hint="YYYY-MM-DD" />
            <app-text-field label="Address" formControlName="address" />
          </section>

          <section id="sec-rollbatch" class="card">
            <h2 class="cc-h3">Roll & batch</h2>
            <app-text-field label="Roll number" formControlName="rollNumber" />
            <app-text-field label="Batch" formControlName="batch" />
          </section>

          <section id="sec-academic" class="card" formGroupName="academic">
            <h2 class="cc-h3">Academic</h2>
            <app-text-field label="Branch" formControlName="branch" />
            <app-text-field label="CGPA" type="text" inputmode="decimal" formControlName="cgpa" [error]="err('academic.cgpa', 'CGPA')" />
            <app-text-field label="Active backlogs" type="text" inputmode="numeric" formControlName="activeBacklogs" [error]="err('academic.activeBacklogs', 'Active backlogs')" />
          </section>

          <section id="sec-placement" class="card" formGroupName="placement">
            <h2 class="cc-h3">Placement</h2>
            <app-text-field label="Skills" formControlName="skills" hint="Comma-separated, e.g. Java, SQL" />
            <app-text-field label="Expected role" formControlName="expectedRole" />
            <app-text-field label="About" formControlName="about" />
          </section>

          @if (!profile()?.isLocked) {
            <div class="actions">
              <app-button variant="secondary" [loading]="saving()" (click)="save()">Save draft</app-button>
              <app-button [loading]="submitting()" [disabled]="profile()?.profileApprovalStatus === 'PENDING_APPROVAL'" (click)="submit()">
                Submit for approval
              </app-button>
            </div>
          }
        </form>

        <aside class="side">
          <div class="card ring">
            <button class="ring-btn" type="button" (click)="jumpToIncomplete()" aria-label="Jump to the first incomplete section">
              <app-progress-ring [percent]="completion()" />
            </button>
            <p class="cc-small muted">{{ completion() }}% complete</p>
          </div>

          <div class="card">
            <h2 class="cc-h3">Resume</h2>
            @if (resume()?.hasResume) {
              <p class="cc-body">{{ resume()?.originalName }} <span class="muted">· v{{ resume()?.version }}</span></p>
              @if (resume()?.previewUrl) {
                <a class="link" [href]="resume()?.previewUrl" target="_blank" rel="noopener">Preview</a>
              }
            } @else {
              <p class="cc-body muted">No resume uploaded yet.</p>
            }
            @if (resumeError()) {
              <p class="field-error cc-small" role="alert">{{ resumeError() }}</p>
            }
            @if (!profile()?.isLocked) {
              <label class="upload cc-body-medium">
                {{ resume()?.hasResume ? 'Replace (PDF)' : 'Upload (PDF)' }}
                <input type="file" accept="application/pdf" (change)="onFile($event)" hidden />
              </label>
            }
          </div>
        </aside>
      </div>
    }
  `,
  styles: [
    `
      .head {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
        margin-bottom: var(--cc-space-6);
      }
      .head h1 {
        margin: 0;
      }
      .banner {
        margin: 0 0 var(--cc-space-4);
        padding: var(--cc-space-3) var(--cc-space-4);
        border-radius: var(--cc-radius-sm);
        font: var(--cc-text-small);
      }
      .banner--lock {
        background: var(--cc-color-surface);
        color: var(--cc-color-text-secondary);
      }
      .banner--reject {
        background: var(--cc-color-danger-subtle);
        color: var(--cc-color-danger);
      }
      .layout {
        display: grid;
        grid-template-columns: 1fr 280px;
        gap: var(--cc-gutter);
        align-items: start;
      }
      .form {
        display: flex;
        flex-direction: column;
        gap: var(--cc-gutter);
      }
      .card {
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
        padding: var(--cc-space-6);
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
      }
      .card h2 {
        margin: 0;
      }
      .actions {
        display: flex;
        gap: var(--cc-space-3);
        justify-content: flex-end;
      }
      .side {
        display: flex;
        flex-direction: column;
        gap: var(--cc-gutter);
      }
      .ring {
        align-items: center;
      }
      .ring-btn {
        all: unset;
        cursor: pointer;
        border-radius: var(--cc-radius-full);
      }
      .ring-btn:focus-visible {
        outline: 2px solid var(--cc-color-primary);
        outline-offset: 2px;
      }
      .muted {
        color: var(--cc-color-text-secondary);
      }
      .field-error {
        color: var(--cc-color-danger);
        margin: 0;
      }
      .upload {
        display: inline-block;
        cursor: pointer;
        color: var(--cc-color-primary);
      }
      .link {
        background: none;
        border: none;
        padding: 0;
        color: var(--cc-color-primary);
        cursor: pointer;
        text-decoration: underline;
      }
      @media (max-width: 1024px) {
        .layout {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class ProfilePage {
  private readonly fb = inject(FormBuilder);
  private readonly profileSvc = inject(ProfileService);
  private readonly toast = inject(ToastService);

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly profile = signal<StudentProfile | null>(null);
  readonly resume = signal<ResumeView | null>(null);
  readonly resumeError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly submitting = signal(false);
  readonly completion = computed(() => this.profile()?.completionPercent ?? 0);

  readonly form = this.fb.nonNullable.group({
    personal: this.fb.nonNullable.group({
      fullName: [''],
      phone: [''],
      gender: [''],
      dateOfBirth: [''],
      address: [''],
    }),
    academic: this.fb.nonNullable.group({
      branch: [''],
      cgpa: ['', [Validators.pattern(NUMERIC), Validators.min(0), Validators.max(10)]],
      activeBacklogs: ['', [Validators.pattern(/^\d+$/), Validators.min(0)]],
    }),
    placement: this.fb.nonNullable.group({
      skills: [''],
      expectedRole: [''],
      about: [''],
    }),
    rollNumber: [''],
    batch: [''],
  });

  constructor() {
    void this.reload();
  }

  statusLabel(p: StudentProfile): string {
    return profileStatusLabel(p.profileApprovalStatus);
  }
  statusVariant(p: StudentProfile) {
    return statusToVariant(p.profileApprovalStatus);
  }

  err(path: string, label: string): string {
    const c = this.form.get(path);
    if (c && c.touched && c.invalid) {
      return c.errors?.['pattern'] ? `${label} must be a number.` : `${label} is out of range.`;
    }
    return '';
  }

  /** The profile sections in display order, each with the control paths it owns (AC3). */
  private readonly SECTIONS: { id: string; paths: string[] }[] = [
    { id: 'sec-personal', paths: ['personal.fullName', 'personal.phone', 'personal.gender', 'personal.dateOfBirth', 'personal.address'] },
    { id: 'sec-rollbatch', paths: ['rollNumber', 'batch'] },
    { id: 'sec-academic', paths: ['academic.branch', 'academic.cgpa', 'academic.activeBacklogs'] },
    { id: 'sec-placement', paths: ['placement.skills', 'placement.expectedRole', 'placement.about'] },
  ];

  /** Scroll the first section that has a blank field into view (the ring jumps to the first incomplete section). */
  jumpToIncomplete(): void {
    const section = this.SECTIONS.find((s) => s.paths.some((p) => this.isBlank(p)));
    if (!section) {
      return;
    }
    document.getElementById(section.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  private isBlank(path: string): boolean {
    const v = this.form.get(path)?.value;
    return v == null || String(v).trim() === '';
  }

  async reload(): Promise<void> {
    this.state.set('loading');
    try {
      const [profile, resume] = await Promise.all([this.profileSvc.getProfile(), this.profileSvc.getResume()]);
      this.applyProfile(profile);
      this.resume.set(resume);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  private applyProfile(p: StudentProfile): void {
    this.profile.set(p);
    this.form.patchValue({
      personal: {
        fullName: p.personal.fullName ?? '',
        phone: p.personal.phone ?? '',
        gender: p.personal.gender ?? '',
        dateOfBirth: p.personal.dateOfBirth ?? '',
        address: p.personal.address ?? '',
      },
      academic: {
        branch: p.academic.branch ?? '',
        cgpa: p.academic.cgpa != null ? String(p.academic.cgpa) : '',
        activeBacklogs: p.academic.activeBacklogs != null ? String(p.academic.activeBacklogs) : '',
      },
      placement: {
        skills: (p.placement.skills ?? []).join(', '),
        expectedRole: p.placement.expectedRole ?? '',
        about: p.placement.about ?? '',
      },
      rollNumber: p.rollNumber ?? '',
      batch: p.batch ?? '',
    });
    if (p.isLocked) {
      this.form.disable({ emitEvent: false });
    }
  }

  private buildRequest(): StudentProfileRequest {
    const v = this.form.getRawValue();
    const num = (s: string): number | null => (s.trim() === '' ? null : Number(s));
    const str = (s: string): string | null => (s.trim() === '' ? null : s.trim());
    return {
      personal: {
        fullName: str(v.personal.fullName),
        phone: str(v.personal.phone),
        gender: str(v.personal.gender),
        dateOfBirth: str(v.personal.dateOfBirth),
        address: str(v.personal.address),
      },
      academic: {
        branch: str(v.academic.branch),
        cgpa: num(v.academic.cgpa),
        activeBacklogs: num(v.academic.activeBacklogs),
      },
      placement: {
        skills: v.placement.skills.trim() ? v.placement.skills.split(',').map((s) => s.trim()).filter(Boolean) : null,
        expectedRole: str(v.placement.expectedRole),
        about: str(v.placement.about),
      },
      rollNumber: str(v.rollNumber),
      batch: str(v.batch),
    };
  }

  async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    try {
      this.applyProfile(await this.profileSvc.saveProfile(this.buildRequest()));
      this.toast.success('Profile saved.');
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not save your profile.');
    } finally {
      this.saving.set(false);
    }
  }

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    try {
      this.applyProfile(await this.profileSvc.submitProfile());
      this.toast.success('Profile submitted for approval.');
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Complete every required field before submitting.');
    } finally {
      this.submitting.set(false);
    }
  }

  async onFile(event: Event): Promise<void> {
    this.resumeError.set(null);
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    if (file.type !== 'application/pdf') {
      this.resumeError.set('Resume must be a PDF.');
      input.value = '';
      return;
    }
    if (file.size > MAX_RESUME_BYTES) {
      this.resumeError.set(`Resume must be a PDF under ${MAX_RESUME_MB} MB.`);
      input.value = '';
      return;
    }
    try {
      this.resume.set(await this.profileSvc.uploadResume(file));
      this.toast.success('Resume uploaded.');
    } catch (e) {
      this.resumeError.set(toAuthErrorView(e).formMessage ?? 'Could not upload your resume.');
    } finally {
      input.value = '';
    }
  }
}
