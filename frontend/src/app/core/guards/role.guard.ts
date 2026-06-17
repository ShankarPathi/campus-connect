import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { PORTAL_FOR_ROLE, Role } from '../auth/auth.models';
import { AuthStore } from '../auth/auth.store';

/**
 * A `CanMatch` guard factory (Story 9.2): the route's required role must equal the session role. A mismatch
 * redirects to the caller's own portal (no cross-portal access). Runs after {@link authGuard}, so the session
 * is already established.
 */
export function requireRole(role: Role): CanMatchFn {
  return () => {
    const store = inject(AuthStore);
    const router = inject(Router);

    if (store.role() === role) {
      return true;
    }
    const own = store.role() ? PORTAL_FOR_ROLE[store.role()!] : null;
    return router.createUrlTree([own ? `/${own}` : '/login']);
  };
}
