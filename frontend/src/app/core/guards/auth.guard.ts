import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { AuthStore } from '../auth/auth.store';

/**
 * Admits a route only with a valid session (Story 9.2) — an in-memory access token, or a successful silent
 * refresh. Otherwise redirects to /login with the attempted URL as `returnUrl`. A `CanMatch` guard so an
 * unauthenticated user never even downloads the portal chunk.
 */
export const authGuard: CanMatchFn = async (_route, segments) => {
  const store = inject(AuthStore);
  const auth = inject(AuthService);
  const router = inject(Router);

  if (store.isAuthenticated()) {
    return true;
  }
  await auth.bootstrap(); // try a one-shot silent refresh (HttpOnly cookie)
  if (store.isAuthenticated()) {
    return true;
  }
  const returnUrl = '/' + segments.map((s) => s.path).join('/');
  return router.createUrlTree(['/login'], { queryParams: { returnUrl } });
};
