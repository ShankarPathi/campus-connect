import { Component, inject, signal } from '@angular/core';
import { Button, ToastService } from '../../../shared/ui';
import { toAuthErrorView } from '../../../core/auth/auth.errors';
import { StudentNotificationsService } from '../student.services';
import { StudentNotification } from '../student.models';

/** A leading glyph per notification type so the feed reads by icon + text, not text alone. */
function notificationIcon(type: string | null | undefined): string {
  const t = (type ?? '').toUpperCase();
  if (t.includes('OFFER')) return '🎁';
  if (t.includes('DRIVE')) return '📢';
  if (t.includes('PLACE')) return '🏆';
  if (t.includes('PROFILE') || t.includes('APPROV') || t.includes('REJECT')) return '✅';
  if (t.includes('APPLI') || t.includes('SHORTLIST') || t.includes('INTERVIEW') || t.includes('SELECT')) return '📄';
  return '🔔';
}

/**
 * Student Notifications screen (Story 9.4, AC10). Lists the student's notifications newest-first with
 * five view states (loading, error+retry, empty, list). Unread rows carry a non-color cue (an "Unread"
 * marker plus a bold title) alongside a colored accent so the distinction never relies on color alone.
 * Header offers "Mark all read" when anything is unread; each unread row has a per-row "Mark read".
 */
@Component({
  selector: 'app-student-notifications',
  standalone: true,
  imports: [Button],
  template: `
    <section class="page">
      <header class="page__head">
        <h1 class="cc-h2">Notifications</h1>
        @if (unreadCount() > 0) {
          <app-button size="sm" (click)="markAll()">Mark all read</app-button>
        }
      </header>

      @if (loading()) {
        <p class="cc-body state">Loading notifications…</p>
      } @else if (error()) {
        <div class="state">
          <p class="cc-body">We couldn't load your notifications.</p>
          <app-button size="sm" variant="secondary" (click)="reload()">Retry</app-button>
        </div>
      } @else if (items().length === 0) {
        <div class="card empty" role="status">
          <span class="empty__icon" aria-hidden="true">🔔</span>
          <p class="empty__title cc-body-medium">You're all caught up</p>
          <p class="empty__sub cc-small">New updates about your applications and offers will appear here.</p>
        </div>
      } @else {
        <ul class="list">
          @for (n of items(); track n.id) {
            <li class="row" [class.row--unread]="!n.isRead">
              <span class="row__icon" aria-hidden="true">{{ icon(n.type) }}</span>
              <div class="row__main">
                <div class="row__top">
                  @if (!n.isRead) {
                    <span class="row__marker">Unread</span>
                  }
                  <span [class]="n.isRead ? 'cc-body' : 'cc-body-medium'">{{ n.title }}</span>
                </div>
                @if (n.message) {
                  <p class="row__message">{{ n.message }}</p>
                }
                <time class="row__date">{{ formatDate(n.createdAt) }}</time>
              </div>
              @if (!n.isRead) {
                <app-button variant="ghost" size="sm" (click)="markRead(n.id)">Mark read</app-button>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: [
    `
      .page {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-4);
      }
      .page__head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--cc-space-4);
      }
      .state {
        color: var(--cc-color-text-secondary);
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--cc-space-3);
      }
      .list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-3);
      }
      .row {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--cc-space-3);
        padding: var(--cc-space-4);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-left: 3px solid transparent;
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-sm);
      }
      .row--unread {
        border-left-color: var(--cc-color-primary);
        background: var(--cc-color-primary-subtle);
      }
      .row__icon {
        font-size: 18px;
        width: 38px;
        height: 38px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--cc-radius-full);
        background: var(--cc-portal-soft, var(--cc-color-primary-subtle));
        flex: none;
      }
      .row--unread .row__icon {
        background: #fff;
      }
      .row__main {
        flex: 1;
      }
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: var(--cc-space-2);
        padding: var(--cc-space-10) var(--cc-space-6);
        background: var(--cc-color-surface-raised);
        border: 1px solid var(--cc-color-border);
        border-radius: var(--cc-radius-lg);
      }
      .empty__icon {
        font-size: 40px;
        width: 80px;
        height: 80px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--cc-radius-full);
        background: var(--cc-portal-soft, var(--cc-color-primary-subtle));
        margin-bottom: var(--cc-space-2);
      }
      .empty__title {
        margin: 0;
      }
      .empty__sub {
        margin: 0;
        color: var(--cc-color-text-secondary);
        max-width: 380px;
      }
      .row__main {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-2);
        min-width: 0;
      }
      .row__top {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
        font: var(--cc-text-body-medium);
        color: var(--cc-color-text);
      }
      .row__marker {
        font: var(--cc-text-small);
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--cc-color-primary);
      }
      .row__message {
        margin: 0;
        font: var(--cc-text-body);
        color: var(--cc-color-text-secondary);
      }
      .row__date {
        font: var(--cc-text-small);
        color: var(--cc-color-text-secondary);
      }
    `,
  ],
})
export class NotificationsPage {
  private readonly service = inject(StudentNotificationsService);
  private readonly toast = inject(ToastService);

  readonly items = signal<StudentNotification[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly unreadCount = this.service.unreadCount;

  /** Template passthrough — the leading glyph for a notification's type. */
  readonly icon = notificationIcon;

  constructor() {
    void this.reload();
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    this.error.set(false);
    try {
      const list = await this.service.list();
      this.items.set(list.items);
      this.service.unreadCount.set(list.unreadCount);
    } catch (e) {
      this.error.set(true);
      this.toast.error(toAuthErrorView(e).formMessage ?? 'Could not load your notifications.');
    } finally {
      this.loading.set(false);
    }
  }

  async markRead(id: string): Promise<void> {
    try {
      await this.service.markRead(id);
      this.items.update((rows) => rows.map((r) => (r.id === id ? { ...r, isRead: true } : r)));
    } catch {
      this.toast.error('Could not mark that as read.');
    }
  }

  async markAll(): Promise<void> {
    try {
      await this.service.markAll();
      this.items.update((rows) => rows.map((r) => ({ ...r, isRead: true })));
    } catch {
      this.toast.error('Could not mark all as read.');
    }
  }

  formatDate(createdAt: string): string {
    return new Date(createdAt).toLocaleString();
  }
}
