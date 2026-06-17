import { Routes } from '@angular/router';

/** Student portal child routes (Story 9.4) — the real screens, default and fallback to the dashboard. */
export const STUDENT_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard').then((m) => m.DashboardPage) },
  { path: 'drives', loadComponent: () => import('./drives/drives').then((m) => m.DrivesPage) },
  { path: 'applications', loadComponent: () => import('./applications/applications').then((m) => m.ApplicationsPage) },
  { path: 'profile', loadComponent: () => import('./profile/profile').then((m) => m.ProfilePage) },
  { path: 'offers', loadComponent: () => import('./offers/offers').then((m) => m.OffersPage) },
  { path: 'notifications', loadComponent: () => import('./notifications/notifications').then((m) => m.NotificationsPage) },
  { path: '**', redirectTo: 'dashboard' },
];
