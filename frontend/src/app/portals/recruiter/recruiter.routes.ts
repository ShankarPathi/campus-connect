import { Routes } from '@angular/router';

/** Recruiter portal child routes (Story 9.5). Applicants/Interviews/Offers are tabs inside the drive workspace. */
export const RECRUITER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard').then((m) => m.RecruiterDashboardPage) },
  { path: 'drives', pathMatch: 'full', loadComponent: () => import('./drives/drives-list').then((m) => m.DrivesListPage) },
  { path: 'drives/new', loadComponent: () => import('./drives/drive-form').then((m) => m.DriveFormPage) },
  { path: 'drives/:driveId', loadComponent: () => import('./drives/workspace').then((m) => m.DriveWorkspacePage) },
  { path: '**', redirectTo: 'dashboard' },
];
