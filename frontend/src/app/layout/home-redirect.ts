import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { PORTAL_FOR_ROLE } from '../core/auth/auth.models';
import { AuthStore } from '../core/auth/auth.store';

/** Root landing inside the shell (Story 9.2): forwards an authenticated user to their role's portal. */
@Component({ selector: 'app-home-redirect', standalone: true, template: '' })
export class HomeRedirect {
  constructor() {
    const store = inject(AuthStore);
    const router = inject(Router);
    const role = store.role();
    const portal = role ? PORTAL_FOR_ROLE[role] : null;
    void router.navigate([portal ? `/${portal}` : '/login']);
  }
}
