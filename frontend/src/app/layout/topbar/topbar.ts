import { Component, inject, output } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';

/**
 * App-shell topbar (Story 9.2, UX-DR2): logo + role tag, the college/tenant name, a notification bell, and a
 * profile control with logout. The hamburger emits a drawer toggle for the responsive sidebar.
 */
@Component({
  selector: 'app-topbar',
  standalone: true,
  template: `
    <header class="topbar">
      <button class="icon-btn hamburger" type="button" aria-label="Toggle navigation" (click)="menuToggle.emit()">
        ☰
      </button>
      <span class="logo cc-h3">Campus Connect</span>
      @if (store.role()) {
        <span class="role-tag cc-caption">{{ store.role() }}</span>
      }
      <span class="spacer"></span>
      @if (store.tenantName()) {
        <span class="tenant cc-body-medium" data-test="tenant-name">{{ store.tenantName() }}</span>
      }
      <button class="icon-btn bell" type="button" aria-label="Notifications">🔔</button>
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
  readonly menuToggle = output<void>();

  logout(): void {
    void this.auth.logout();
  }
}
