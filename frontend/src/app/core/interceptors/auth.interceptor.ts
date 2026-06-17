import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, map, Observable, shareReplay, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { AuthStore } from '../auth/auth.store';

/** Shared in-flight refresh so concurrent 401s trigger exactly one /refresh (single-flight). */
let refresh$: Observable<string> | null = null;

const AUTH_ENDPOINT = /\/api\/(student|recruiter|admin)\/auth\//;

/**
 * Attaches `Authorization: Bearer <accessToken>` to API requests (skipping the per-portal auth endpoints), and
 * on a 401 performs a single-flight refresh + one retry (Story 9.2). A failed refresh clears the session and
 * redirects to /login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const auth = inject(AuthService);
  const router = inject(Router);

  const isApi = req.url.startsWith('/api');
  const isAuthEndpoint = AUTH_ENDPOINT.test(req.url);
  const token = store.accessToken();

  const request =
    isApi && !isAuthEndpoint && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || isAuthEndpoint || !isApi) {
        return throwError(() => err);
      }
      const portal = store.activePortal();
      if (!portal) {
        store.clear();
        void router.navigate(['/login']);
        return throwError(() => err);
      }
      if (!refresh$) {
        refresh$ = auth.refresh(portal).pipe(
          map((r) => r.accessToken),
          finalize(() => (refresh$ = null)),
          shareReplay(1),
        );
      }
      return refresh$.pipe(
        switchMap((newToken) => next(req.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } }))),
        catchError((refreshErr: unknown) => {
          store.clear();
          void router.navigate(['/login']);
          return throwError(() => refreshErr);
        }),
      );
    }),
  );
};
