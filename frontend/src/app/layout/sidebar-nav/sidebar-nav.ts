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
        position: relative;
        display: flex;
        align-items: center;
        gap: var(--cc-space-3);
        padding: var(--cc-space-3) var(--cc-space-3);
        border-radius: var(--cc-radius-md);
        color: var(--cc-color-text-secondary);
        text-decoration: none;
        transition:
          background 0.14s ease,
          color 0.14s ease,
          transform 0.14s ease,
          box-shadow 0.14s ease;
      }
      .nav-link__icon {
        font-size: 17px;
        width: 28px;
        height: 28px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--cc-radius-sm);
        background: var(--cc-color-primary-subtle);
        flex: none;
        transition: background 0.14s ease;
      }
      .nav-link:hover {
        background: var(--cc-color-primary-subtle);
        color: var(--cc-color-text);
      }
      /* active = a filled portal-gradient pill — clear colour, not a plain white rail */
      .nav-link--active {
        background: var(--cc-portal-grad, var(--cc-color-primary));
        color: #fff;
        font-weight: 700;
        box-shadow: var(--cc-shadow-sm);
      }
      .nav-link--active .nav-link__icon {
        background: rgba(255, 255, 255, 0.22);
      }
    `,
  ],
})
export class SidebarNav {
  readonly items = input<NavItem[]>([]);
}
