import { Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { Role } from '../../core/auth/auth.models';
import { NavItem, SidebarNav } from '../sidebar-nav/sidebar-nav';
import { Topbar } from '../topbar/topbar';
import { Toast } from '../../shared/ui';

const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  STUDENT: [
    { label: 'Dashboard', path: '/student/dashboard' },
    { label: 'Drives', path: '/student/drives' },
    { label: 'My Applications', path: '/student/applications' },
    { label: 'Profile', path: '/student/profile' },
    { label: 'Offers', path: '/student/offers' },
    { label: 'Notifications', path: '/student/notifications' },
  ],
  RECRUITER: [
    { label: 'Dashboard', path: '/recruiter/dashboard' },
    { label: 'My Drives', path: '/recruiter/drives' },
  ],
  COLLEGE_ADMIN: [
    { label: 'Dashboard', path: '/admin/dashboard' },
    { label: 'Students', path: '/admin/students' },
    { label: 'Recruiters', path: '/admin/recruiters' },
    { label: 'Drives', path: '/admin/drives' },
    { label: 'Placements', path: '/admin/placements' },
    { label: 'Eligibility', path: '/admin/eligibility' },
    { label: 'Reports', path: '/admin/reports' },
  ],
  PLATFORM_ADMIN: [],
};

/**
 * The authenticated app shell (Story 9.2, UX-DR2/UX-DR11): a fixed left sidebar + a topbar wrapping the portal
 * `<router-outlet>`. Under 1024px the sidebar collapses to an off-canvas drawer toggled from the topbar.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, Topbar, SidebarNav, Toast],
  template: `
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <div class="shell" [class.shell--drawer-open]="drawerOpen()">
      <app-topbar [menuExpanded]="drawerOpen()" (menuToggle)="toggleDrawer()" />
      <div class="body">
        <aside class="sidebar" id="primary-sidebar">
          <app-sidebar-nav [items]="navItems()" />
        </aside>
        <div class="scrim" (click)="closeDrawer()"></div>
        <main class="content" id="main-content" tabindex="-1">
          <router-outlet />
        </main>
      </div>
    </div>
    <app-toast />
  `,
  styles: [
    `
      .skip-link {
        position: absolute;
        left: var(--cc-space-2);
        top: -48px;
        z-index: 100;
        padding: var(--cc-space-2) var(--cc-space-4);
        background: var(--cc-color-primary);
        color: var(--cc-color-text-inverse);
        border-radius: var(--cc-radius-sm);
        text-decoration: none;
        transition: top 0.15s ease;
      }
      .skip-link:focus {
        top: var(--cc-space-2);
      }
      .content:focus {
        outline: none;
      }
      .shell {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        background: var(--cc-color-surface);
      }
      .body {
        flex: 1;
        display: flex;
        align-items: stretch;
      }
      .sidebar {
        width: 240px;
        flex: 0 0 240px;
        background: var(--cc-color-surface);
        border-right: 1px solid var(--cc-color-border);
      }
      .content {
        flex: 1;
        max-width: var(--cc-page-max);
        margin: 0 auto;
        width: 100%;
        padding: var(--cc-gutter);
      }
      .scrim {
        display: none;
      }
      @media (max-width: 1024px) {
        .sidebar {
          position: fixed;
          top: 56px;
          bottom: 0;
          left: 0;
          z-index: 20;
          transform: translateX(-100%);
          transition: transform 0.2s ease;
        }
        .shell--drawer-open .sidebar {
          transform: translateX(0);
        }
        .shell--drawer-open .scrim {
          display: block;
          position: fixed;
          inset: 56px 0 0 0;
          background: rgba(0, 0, 0, 0.3);
          z-index: 10;
        }
      }
    `,
  ],
})
export class AppShell {
  private readonly store = inject(AuthStore);
  readonly drawerOpen = signal(false);
  readonly navItems = computed<NavItem[]>(() => {
    const role = this.store.role();
    return role ? NAV_BY_ROLE[role] : [];
  });

  toggleDrawer(): void {
    this.drawerOpen.update((v) => !v);
  }
  closeDrawer(): void {
    this.drawerOpen.set(false);
  }
}
