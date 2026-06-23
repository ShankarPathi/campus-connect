import { Component, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { StudentNotificationsService } from '../../portals/student/student.services';

/**
 * App-shell topbar (Story 9.2, UX-DR2): logo + role tag, the college/tenant name, a notification bell with
 * an unread badge, and a profile control with logout. The hamburger emits a drawer toggle for the
 * responsive sidebar. The bell badge binds to the student notifications unread signal (Story 9.4); the
 * count is refreshed once for a student on shell load.
 */
@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="topbar">
      <button
        class="icon-btn hamburger"
        type="button"
        aria-label="Toggle navigation"
        aria-controls="primary-sidebar"
        [attr.aria-expanded]="menuExpanded()"
        (click)="menuToggle.emit()"
      >
        ☰
      </button>
      <span class="brand">
        <img class="brand__logo" src="icon.svg" alt="" width="28" height="28" />
        <span class="logo cc-h3">CampusConnect</span>
      </span>
      @if (store.role()) {
        <span class="role-tag cc-caption">{{ store.role() }}</span>
      }
      <span class="spacer"></span>
      @if (store.tenantName()) {
        <span class="tenant cc-body-medium" data-test="tenant-name">{{ store.tenantName() }}</span>
      }
      @if (notificationsLink(); as link) {
        <a class="icon-btn bell" [routerLink]="link" [attr.aria-label]="bellLabel()">
          🔔
          @if (unread() > 0) {
            <span class="bell__badge cc-caption" aria-hidden="true">{{ unread() > 99 ? '99+' : unread() }}</span>
          }
        </a>
      } @else {
        <span class="icon-btn bell" aria-hidden="true">🔔</span>
      }
      <button class="logout cc-body-medium" type="button" (click)="logout()">Logout</button>
    </header>
  `,
  styles: [
    `
      .topbar {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
        height: 56px;
        padding: 0 var(--cc-gutter);
        background: var(--cc-color-surface-raised);
        border-bottom: 1px solid var(--cc-color-border);
      }
      .brand {
        display: inline-flex;
        align-items: center;
        gap: var(--cc-space-2);
      }
      .brand__logo {
        border-radius: var(--cc-radius-sm);
      }
      .logo {
        color: var(--cc-color-primary);
      }
      .role-tag {
        padding: 2px 8px;
        border-radius: var(--cc-radius-full);
        background: var(--cc-color-primary-subtle);
        color: var(--cc-color-primary);
        letter-spacing: 0.05em;
        text-transform: uppercase;
      }
      .spacer {
        flex: 1;
      }
      .tenant {
        color: var(--cc-color-text-secondary);
      }
      .icon-btn {
        border: none;
        background: transparent;
        cursor: pointer;
        font-size: 18px;
        line-height: 1;
        color: var(--cc-color-text-secondary);
      }
      .bell {
        position: relative;
        text-decoration: none;
      }
      .bell__badge {
        position: absolute;
        top: -4px;
        right: -6px;
        min-width: 16px;
        height: 16px;
        padding: 0 4px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--cc-radius-full);
        background: var(--cc-color-danger);
        color: var(--cc-color-text-inverse);
        font-size: 10px;
      }
      .hamburger {
        display: none;
      }
      .logout {
        border: 1px solid var(--cc-color-border-strong);
        background: var(--cc-color-surface-raised);
        color: var(--cc-color-text);
        border-radius: var(--cc-radius-sm);
        padding: var(--cc-space-1) var(--cc-space-3);
        cursor: pointer;
      }
      @media (max-width: 1024px) {
        .hamburger {
          display: inline-flex;
        }
      }
    `,
  ],
})
export class Topbar {
  protected readonly store = inject(AuthStore);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(StudentNotificationsService);
  /** Whether the off-canvas drawer is currently open — reflected as the hamburger's aria-expanded. */
  readonly menuExpanded = input(false);
  readonly menuToggle = output<void>();

  /** Unread badge count (student notifications; other portals wire their own later). */
  readonly unread = this.notifications.unreadCount;

  constructor() {
    if (this.store.role() === 'STUDENT') {
      void this.notifications.refreshUnread().catch(() => {});
    }
  }

  /** The bell links to notifications only where a notifications screen exists (student today). */
  notificationsLink(): string | null {
    return this.store.role() === 'STUDENT' ? '/student/notifications' : null;
  }

  bellLabel(): string {
    const n = this.unread();
    return n > 0 ? `Notifications, ${n} unread` : 'Notifications';
  }

  logout(): void {
    void this.auth.logout();
  }
}
