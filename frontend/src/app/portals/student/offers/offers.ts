import { Component, computed, inject, signal } from '@angular/core';
import { OffersService } from '../student.services';
import { OfferDetail, OfferSummary } from '../student.models';
import { offerStatusLabel } from '../student.mappers';
import { Button, Modal, StatusPill, ToastService, statusToVariant } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';

type LoadState = 'loading' | 'error' | 'ready';

/**
 * Student "Offers" screen (Story 9.4 / AC9). Lists the offers a recruiter has released, opens a detail
 * modal with the full terms and a presigned offer-letter link, and lets the student accept or decline a
 * PENDING offer behind an inline confirm step. Errors are surfaced as plain-language toasts (UX-DR12).
 *
 * The offer-letter URL is a fresh presigned link fetched per detail open; it is held only on the
 * `selected` signal and never cached elsewhere.
 */
@Component({
  selector: 'app-student-offers',
  standalone: true,
  imports: [Button, Modal, StatusPill],
  template: `
    <section class="offers">
      <h1 class="cc-h2">Offers</h1>

      @if (state() === 'loading') {
        <p class="cc-body muted">Loading your offers…</p>
      } @else if (state() === 'error') {
        <div class="state">
          <p class="cc-body">We couldn't load your offers.</p>
          <app-button (click)="load()">Retry</app-button>
        </div>
      } @else if (offers().length === 0) {
        <p class="cc-body muted">No offers yet — they'll appear here once a recruiter releases one.</p>
      } @else {
        <ul class="cards">
          @for (offer of offers(); track offer.id) {
            <li class="card">
              <div class="card__head">
                <h2 class="card__role">{{ offer.role }}</h2>
                <app-status-pill [label]="offerStatusLabel(offer.status)" [variant]="statusToVariant(offer.status)" />
              </div>
              <dl class="terms">
                @if (offer.ctc !== null) {
                  <div class="terms__row"><dt>CTC</dt><dd>₹{{ offer.ctc }} LPA</dd></div>
                }
                @if (offer.joiningDate) {
                  <div class="terms__row"><dt>Joining</dt><dd>{{ formatDate(offer.joiningDate) }}</dd></div>
                }
                @if (offer.acceptanceDeadline) {
                  <div class="terms__row"><dt>Respond by</dt><dd>{{ formatDate(offer.acceptanceDeadline) }}</dd></div>
                }
              </dl>
              <app-button size="sm" variant="secondary" (click)="openDetail(offer.id)">View details</app-button>
            </li>
          }
        </ul>
      }
    </section>

    <app-modal [(open)]="detailOpen" [title]="selected()?.role ?? 'Offer'" (closed)="onModalClosed()">
      @if (selected(); as offer) {
        <dl class="terms terms--modal">
          <div class="terms__row"><dt>Role</dt><dd>{{ offer.role }}</dd></div>
          <div class="terms__row"><dt>Status</dt><dd>{{ offerStatusLabel(offer.status) }}</dd></div>
          @if (offer.ctc !== null) {
            <div class="terms__row"><dt>CTC</dt><dd>₹{{ offer.ctc }} LPA</dd></div>
          }
          @if (offer.joiningDate) {
            <div class="terms__row"><dt>Joining date</dt><dd>{{ formatDate(offer.joiningDate) }}</dd></div>
          }
          @if (offer.acceptanceDeadline) {
            <div class="terms__row"><dt>Acceptance deadline</dt><dd>{{ formatDate(offer.acceptanceDeadline) }}</dd></div>
          }
        </dl>

        @if (offer.offerLetterUrl) {
          <p class="letter">
            <a [href]="offer.offerLetterUrl" target="_blank" rel="noopener">View / download offer letter</a>
          </p>
        }

        @if (confirming()) {
          <p class="confirm cc-body" role="alert">{{ confirmPrompt() }}</p>
        }
      }

      <div footer>
        @if (selected()?.status === 'PENDING') {
          @if (confirming() === 'accept') {
            <app-button variant="secondary" [disabled]="busy()" (click)="cancelConfirm()">Cancel</app-button>
            <app-button [loading]="busy()" (click)="accept()">Confirm accept</app-button>
          } @else if (confirming() === 'decline') {
            <app-button variant="secondary" [disabled]="busy()" (click)="cancelConfirm()">Cancel</app-button>
            <app-button variant="danger" [loading]="busy()" (click)="decline()">Confirm decline</app-button>
          } @else {
            <app-button variant="danger" (click)="askConfirm('decline')">Decline</app-button>
            <app-button (click)="askConfirm('accept')">Accept</app-button>
          }
        }
      </div>
    </app-modal>
  `,
  styles: [
    `
      .offers {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-6);
      }
      .muted {
        color: var(--cc-color-text-secondary);
      }
      .state {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--cc-space-4);
      }
      .cards {
        list-style: none;
        margin: 0;
        padding: 0;
        display: grid;
        gap: var(--cc-space-4);
      }
      .card {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
        align-items: flex-start;
        padding: var(--cc-space-6);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
      }
      .card__head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-3);
        width: 100%;
      }
      .card__role {
        margin: 0;
        font: var(--cc-text-h2);
        color: var(--cc-color-text);
      }
      .terms {
        margin: 0;
        display: grid;
        gap: var(--cc-space-3);
      }
      .terms__row {
        display: flex;
        gap: var(--cc-space-3);
      }
      .terms dt {
        margin: 0;
        min-width: 140px;
        color: var(--cc-color-text-secondary);
        font: var(--cc-text-body);
      }
      .terms dd {
        margin: 0;
        color: var(--cc-color-text);
        font: var(--cc-text-body);
      }
      .letter {
        margin: 0;
      }
      .letter a {
        color: var(--cc-color-primary);
      }
      .confirm {
        margin: 0;
        padding: var(--cc-space-3);
        color: var(--cc-color-text);
      }
    `,
  ],
})
export class OffersPage {
  private readonly offersService = inject(OffersService);
  private readonly toast = inject(ToastService);

  protected readonly offerStatusLabel = offerStatusLabel;
  protected readonly statusToVariant = statusToVariant;

  readonly offers = signal<OfferSummary[]>([]);
  readonly state = signal<LoadState>('loading');

  readonly detailOpen = signal(false);
  readonly selected = signal<OfferDetail | null>(null);
  /** Inline confirm step inside the detail modal (avoids nested modals); null when not confirming. */
  readonly confirming = signal<'accept' | 'decline' | null>(null);
  readonly busy = signal(false);

  readonly confirmPrompt = computed(() =>
    this.confirming() === 'accept'
      ? 'Accept this offer? This confirms your placement and is final.'
      : 'Decline this offer? This cannot be undone.',
  );

  constructor() {
    this.load();
  }

  async load(): Promise<void> {
    this.state.set('loading');
    try {
      this.offers.set(await this.offersService.listOffers());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  async openDetail(id: string): Promise<void> {
    this.confirming.set(null);
    try {
      const detail = await this.offersService.getOffer(id);
      this.selected.set(detail);
      this.detailOpen.set(true);
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not complete that.');
    }
  }

  askConfirm(kind: 'accept' | 'decline'): void {
    this.confirming.set(kind);
  }

  cancelConfirm(): void {
    this.confirming.set(null);
  }

  async accept(): Promise<void> {
    await this.respond('accept');
  }

  async decline(): Promise<void> {
    await this.respond('decline');
  }

  private async respond(kind: 'accept' | 'decline'): Promise<void> {
    const current = this.selected();
    if (!current || this.busy()) {
      return;
    }
    this.busy.set(true);
    try {
      const updated =
        kind === 'accept'
          ? await this.offersService.accept(current.id)
          : await this.offersService.decline(current.id);
      this.applyUpdate(updated);
      this.detailOpen.set(false);
      this.selected.set(null);
      this.confirming.set(null);
      this.toast.success(kind === 'accept' ? 'Offer accepted.' : 'Offer declined.');
    } catch (e) {
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not complete that.');
    } finally {
      this.busy.set(false);
    }
  }

  /** Replace the matching summary in the list with the fresh status from the returned detail. */
  private applyUpdate(detail: OfferDetail): void {
    this.offers.update((list) => list.map((o) => (o.id === detail.id ? { ...o, ...detail } : o)));
  }

  onModalClosed(): void {
    this.confirming.set(null);
    this.selected.set(null);
  }

  protected formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString();
  }
}
