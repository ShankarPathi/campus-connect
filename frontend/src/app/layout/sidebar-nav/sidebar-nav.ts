import { Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface NavItem {
  label: string;
  path: string;
  icon?: string;
}

/**
 * App-shell sidebar navigation (Story 9.2, UX-DR2) — role-scoped links with an active-route highlight.
 * Presentational: the shell supplies the role's `items`.
 */
@Component({
  selector: 'app-sidebar-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="nav" aria-label="Primary">
      @for (item of items(); track item.path) {
        <a
          class="nav-link cc-body-medium"
          [routerLink]="item.path"
          routerLinkActive="nav-link--active"
          ariaCurrentWhenActive="page"
        >
          @if (item.icon) {
            <span class="nav-link__icon" aria-hidden="true">{{ item.icon }}</span>
          }
          {{ item.label }}</a
        >
      }
    </nav>
  `,
  styles: [
    `
      .nav {
        display: flex;
        flex-direction: column;
        gap: var(--cc-space-1);
        padding: var(--cc-space-4) var(--cc-space-3);
      }
      .nav-link {
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
        padding: var(--cc-space-2) var(--cc-space-3);
        border-radius: var(--cc-radius-md);
        color: var(--cc-color-text-secondary);
        text-decoration: none;
        transition:
          background 0.12s ease,
          color 0.12s ease;
      }
      .nav-link__icon {
        font-size: 16px;
        width: 20px;
        text-align: center;
        flex: none;
      }
      .nav-link:hover {
        background: var(--cc-color-primary-subtle);
        color: var(--cc-color-text);
      }
      .nav-link--active {
        background: var(--cc-color-primary-subtle);
        color: var(--cc-color-primary);
        font-weight: 700;
      }
    `,
  ],
})
export class SidebarNav {
  readonly items = input<NavItem[]>([]);
}
