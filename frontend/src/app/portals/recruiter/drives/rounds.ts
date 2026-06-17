import { Component, inject, input, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button, Modal, ToastService } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { ApplicantService, RoundService } from '../recruiter.services';
import { ApplicantSummary, RoundResult, RoundView } from '../recruiter.models';
import { modeLabel } from '../recruiter.mappers';

/**
 * Interviews tab (Story 9.5) — define/replace the ordered round sequence, reschedule a round, and record
 * per-round PASS/FAIL/ABSENT results. There is no per-round roster endpoint, so the results UI lists the
 * drive's in-progress applicants via the applicant query and posts results for the chosen round.
 */
@Component({
  selector: 'app-recruiter-rounds',
  standalone: true,
  imports: [ReactiveFormsModule, Button, Modal],
  template: `
    @if (state() === 'loading') {
      <p class="cc-body">Loading rounds…</p>
    } @else if (state() === 'error') {
      <p class="cc-body">We couldn't load rounds. <button class="link" type="button" (click)="load()">Try again</button></p>
    } @else {
      @if (rounds().length > 0) {
        <ol class="rounds">
          @for (r of rounds(); track r.roundOrder) {
            <li class="round">
              <div>
                <span class="cc-body-medium">Round {{ r.roundOrder }} · {{ r.name }}</span>
                <p class="cc-small muted">{{ mode(r.mode) }} · {{ when(r.schedule) }} · {{ r.venueOrLink }} · {{ r.assignedCount }} assigned</p>
              </div>
              <div class="round__actions">
                <app-button size="sm" variant="ghost" (click)="openReschedule(r)">Reschedule</app-button>
                <app-button size="sm" variant="secondary" (click)="openResults(r)">Record results</app-button>
              </div>
            </li>
          }
        </ol>
        <p class="cc-small muted">Re-defining rounds replaces the whole sequence and re-enrolls shortlisted applicants into round 1.</p>
      } @else {
        <p class="cc-body">No interview rounds yet — define the sequence below. Shortlisted applicants are enrolled into round 1.</p>
      }

      <form class="define" [formGroup]="form">
        <h3 class="cc-h3">{{ rounds().length > 0 ? 'Redefine rounds' : 'Define rounds' }}</h3>
        <div formArrayName="rounds">
          @for (ctrl of roundsArray.controls; track $index) {
            <div class="rowdef" [formGroupName]="$index">
              <input class="inp" formControlName="name" placeholder="Round name" aria-label="Round name" />
              <select class="inp" formControlName="mode" aria-label="Mode">
                <option value="ONLINE">Online</option>
                <option value="OFFLINE">In person</option>
              </select>
              <input class="inp" formControlName="schedule" placeholder="YYYY-MM-DDTHH:mm:ssZ" aria-label="Schedule" />
              <input class="inp" formControlName="venueOrLink" placeholder="Venue or link" aria-label="Venue or link" />
              <app-button size="sm" variant="ghost" (click)="removeRound($index)">Remove</app-button>
            </div>
          }
        </div>
        <div class="define__actions">
          <app-button size="sm" variant="secondary" (click)="addRound()">Add round</app-button>
          <app-button size="sm" [loading]="saving()" (click)="define()">Save sequence</app-button>
        </div>
      </form>
    }

    <app-modal [(open)]="rescheduleOpen" title="Reschedule round">
      <form class="rsform" [formGroup]="rescheduleForm">
        <input class="inp" formControlName="schedule" placeholder="YYYY-MM-DDTHH:mm:ssZ" aria-label="New schedule" />
        <input class="inp" formControlName="venueOrLink" placeholder="Venue or link" aria-label="Venue or link" />
      </form>
      <div footer>
        <app-button variant="ghost" (click)="rescheduleOpen.set(false)">Cancel</app-button>
        <app-button [loading]="saving()" (click)="doReschedule()">Reschedule</app-button>
      </div>
    </app-modal>

    <app-modal [(open)]="resultsOpen" [title]="'Record results · Round ' + (activeRound()?.roundOrder ?? '')">
      @if (applicants().length === 0) {
        <p class="cc-body muted">No in-progress applicants to record.</p>
      } @else {
        <ul class="results">
          @for (a of applicants(); track a.applicationId) {
            <li class="resrow">
              <span class="cc-body">{{ a.fullName }} <span class="muted">· {{ a.rollNumber }}</span></span>
              <div class="seg" role="radiogroup" [attr.aria-label]="'Result for ' + a.fullName">
                @for (opt of resultOptions; track opt) {
                  <button type="button" class="seg__opt" [class.seg__opt--on]="resultFor(a.applicationId) === opt" (click)="setResult(a.applicationId, opt)">
                    {{ opt === 'PASS' ? 'Pass' : opt === 'FAIL' ? 'Fail' : 'Absent' }}
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      }
      <div footer>
        <app-button variant="ghost" (click)="resultsOpen.set(false)">Cancel</app-button>
        <app-button [loading]="saving()" [disabled]="resultCount() === 0" (click)="submitResults()">Save results</app-button>
      </div>
    </app-modal>
  `,
  styles: [
    `
      .rounds {
        list-style: none;
        margin: 0 0 var(--cc-space-4);
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-3);
      }
      .round {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-3);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-md);
        padding: var(--cc-space-4);
      }
      .round__actions {
        display: flex;
        gap: var(--cc-space-2);
      }
      .muted {
        color: var(--cc-color-text-secondary);
        margin: 0;
      }
      .define,
      .rsform {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-3);
        margin-top: var(--cc-space-6);
      }
      .rowdef {
        display: flex;
        flex-wrap: wrap;
        gap: var(--cc-space-2);
        align-items: center;
      }
      .inp {
        font: var(--cc-text-body);
        border: 1px solid var(--cc-color-border-strong);
        border-radius: var(--cc-radius-sm);
        padding: var(--cc-space-2) var(--cc-space-3);
        min-height: 38px;
        flex: 1;
        min-width: 120px;
      }
      .define__actions {
        display: flex;
        gap: var(--cc-space-3);
        justify-content: flex-end;
      }
      .results {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-2);
      }
      .resrow {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-3);
      }
      .seg {
        display: inline-flex;
        gap: var(--cc-space-1);
        padding: var(--cc-space-1);
        background: var(--cc-color-surface);
        border-radius: var(--cc-radius-md);
      }
      .seg__opt {
        font: var(--cc-text-caption);
        border: none;
        background: transparent;
        color: var(--cc-color-text-secondary);
        border-radius: var(--cc-radius-sm);
        padding: 4px 10px;
        cursor: pointer;
      }
      .seg__opt--on {
        background: var(--cc-color-surface-raised);
        color: var(--cc-color-primary);
        box-shadow: var(--cc-shadow-sm);
      }
      .link {
        background: none;
        border: none;
        padding: 0;
        color: var(--cc-color-primary);
        cursor: pointer;
        text-decoration: underline;
      }
    `,
  ],
})
export class RecruiterRounds {
  private readonly fb = inject(FormBuilder);
  private readonly roundSvc = inject(RoundService);
  private readonly applicantSvc = inject(ApplicantService);
  private readonly toast = inject(ToastService);
  protected readonly resultOptions: RoundResult[] = ['PASS', 'FAIL', 'ABSENT'];

  readonly driveId = input.required<string>();

  readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  readonly rounds = signal<RoundView[]>([]);
  readonly saving = signal(false);
  readonly rescheduleOpen = signal(false);
  readonly resultsOpen = signal(false);
  readonly activeRound = signal<RoundView | null>(null);
  readonly applicants = signal<ApplicantSummary[]>([]);
  private readonly results = signal<Record<string, RoundResult>>({});

  readonly form = this.fb.group({ rounds: this.fb.array([this.newRound()]) });
  readonly rescheduleForm = this.fb.nonNullable.group({ schedule: ['', Validators.required], venueOrLink: [''] });

  get roundsArray(): FormArray {
    return this.form.get('rounds') as FormArray;
  }

  constructor() {
    queueMicrotask(() => this.load());
  }

  mode = modeLabel;
  when(iso: string): string {
    return new Date(iso).toLocaleString();
  }

  private newRound() {
    return this.fb.nonNullable.group({
      name: ['', Validators.required],
      mode: ['ONLINE'],
      schedule: ['', Validators.required],
      venueOrLink: ['', Validators.required],
    });
  }
  addRound(): void {
    this.roundsArray.push(this.newRound());
  }
  removeRound(i: number): void {
    if (this.roundsArray.length > 1) {
      this.roundsArray.removeAt(i);
    }
  }

  async load(): Promise<void> {
    this.state.set('loading');
    try {
      const res = await this.roundSvc.getRounds(this.driveId());
      this.rounds.set(res.rounds);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  async define(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.error('Fill in every round field.');
      return;
    }
    this.saving.set(true);
    try {
      const body = { rounds: this.roundsArray.getRawValue() as { name: string; mode: 'ONLINE' | 'OFFLINE'; schedule: string; venueOrLink: string }[] };
      const res = await this.roundSvc.defineRounds(this.driveId(), body);
      this.rounds.set(res.rounds);
      this.toast.success('Interview rounds defined.');
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not define rounds.');
    } finally {
      this.saving.set(false);
    }
  }

  openReschedule(r: RoundView): void {
    this.activeRound.set(r);
    this.rescheduleForm.setValue({ schedule: r.schedule, venueOrLink: r.venueOrLink });
    this.rescheduleOpen.set(true);
  }
  async doReschedule(): Promise<void> {
    const r = this.activeRound();
    if (!r || this.rescheduleForm.invalid) {
      return;
    }
    this.saving.set(true);
    try {
      const res = await this.roundSvc.reschedule(this.driveId(), r.roundOrder, this.rescheduleForm.getRawValue());
      this.rounds.set(res.rounds);
      this.toast.success('Round rescheduled.');
      this.rescheduleOpen.set(false);
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not reschedule.');
    } finally {
      this.saving.set(false);
    }
  }

  async openResults(r: RoundView): Promise<void> {
    this.activeRound.set(r);
    this.results.set({});
    this.resultsOpen.set(true);
    try {
      // No per-round roster endpoint — list the drive's in-progress applicants to record against.
      const page = await this.applicantSvc.list(this.driveId(), { status: ['SHORTLISTED', 'INTERVIEWING'], pageSize: 200 });
      this.applicants.set(page.items);
    } catch {
      this.applicants.set([]);
    }
  }
  resultFor(id: string): RoundResult | undefined {
    return this.results()[id];
  }
  setResult(id: string, result: RoundResult): void {
    this.results.update((m) => ({ ...m, [id]: result }));
  }
  resultCount(): number {
    return Object.keys(this.results()).length;
  }
  async submitResults(): Promise<void> {
    const r = this.activeRound();
    if (!r) {
      return;
    }
    const entries = Object.entries(this.results()).map(([applicationId, result]) => ({ applicationId, result }));
    if (!entries.length) {
      return;
    }
    this.saving.set(true);
    try {
      const res = await this.roundSvc.recordResults(this.driveId(), r.roundOrder, { results: entries });
      this.toast.success(res.failedCount > 0 ? `${res.succeededCount} recorded, ${res.failedCount} failed.` : 'Results recorded.');
      this.resultsOpen.set(false);
      await this.load();
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not record results.');
    } finally {
      this.saving.set(false);
    }
  }
}
