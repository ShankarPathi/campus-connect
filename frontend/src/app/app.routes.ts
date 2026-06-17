import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { requireRole } from './core/guards/role.guard';

/**
 * Root routes (Story 9.2; auth screens 9.3): the public auth screens outside the shell, and the
 * authenticated shell wrapping three lazy, role-gated portals (CanMatch — the wrong-role/unauthenticated
 * chunk is never downloaded).
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register/register').then((m) => m.Register),
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./auth/verify-email/verify-email').then((m) => m.VerifyEmail),
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: '',
    canMatch: [authGuard],
    loadComponent: () => import('./layout/app-shell/app-shell').then((m) => m.AppShell),
    children: [
      {
        path: 'student',
        canMatch: [requireRole('STUDENT')],
        loadChildren: () => import('./portals/student/student.routes').then((m) => m.STUDENT_ROUTES),
      },
      {
        path: 'recruiter',
        canMatch: [requireRole('RECRUITER')],
        loadChildren: () => import('./portals/recruiter/recruiter.routes').then((m) => m.RECRUITER_ROUTES),
      },
      {
        path: 'admin',
        canMatch: [requireRole('COLLEGE_ADMIN')],
        loadChildren: () => import('./portals/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./layout/home-redirect').then((m) => m.HomeRedirect),
      },
      {
        // an authenticated but unknown in-shell deep link → forward to the user's portal (no empty shell)
        path: '**',
        loadComponent: () => import('./layout/home-redirect').then((m) => m.HomeRedirect),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
