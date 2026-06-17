import { Routes } from '@angular/router';

/** Admin portal child routes (Story 9.6). */
export const ADMIN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard').then((m) => m.AdminDashboardPage) },
  { path: 'students', loadComponent: () => import('./students/students').then((m) => m.StudentApprovalsPage) },
  { path: 'recruiters', loadComponent: () => import('./recruiters/recruiters').then((m) => m.RecruiterApprovalsPage) },
  { path: 'drives', loadComponent: () => import('./drives/drives').then((m) => m.DriveApprovalsPage) },
  { path: 'placements', loadComponent: () => import('./placements/placements').then((m) => m.PlacementsPage) },
  { path: 'eligibility', loadComponent: () => import('./eligibility/eligibility').then((m) => m.EligibilityPolicyPage) },
  { path: 'reports', loadComponent: () => import('./reports/reports').then((m) => m.ReportsPage) },
  { path: '**', redirectTo: 'dashboard' },
];
