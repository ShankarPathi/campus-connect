import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, Portal, RefreshResponse } from './auth.models';
import { AuthStore } from './auth.store';

/**
 * Auth flows against the per-portal Epic-2 endpoints (Story 9.2). Responses arrive already unwrapped from the
 * `ApiResponse` envelope by the response interceptor. The HttpOnly `refreshToken` cookie is set/rotated by the
 * server; `withCredentials` keeps it flowing (same-origin via the dev proxy / Caddy).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  private authUrl(portal: Portal, op: string): string {
    return `${environment.apiBase}/${portal}/auth/${op}`;
  }

  /** Authenticate against a portal; stores the session on success. */
  async login(portal: Portal, credentials: LoginRequest): Promise<LoginResponse> {
    const res = await firstValueFrom(
      this.http.post<LoginResponse>(this.authUrl(portal, 'login'), credentials, { withCredentials: true }),
    );
    this.store.setSession(res.accessToken, portal);
    return res;
  }

  /** Rotate the session — a fresh access token in the body, a rotated refresh cookie. Returns the new token. */
  refresh(portal: Portal): Observable<RefreshResponse> {
    return this.http
      .post<RefreshResponse>(this.authUrl(portal, 'refresh'), {}, { withCredentials: true })
      .pipe(tap((res) => this.store.setAccessToken(res.accessToken)));
  }

  /** End the session (best-effort server call), clear state, and return to login. Idempotent. */
  async logout(): Promise<void> {
    const portal = this.store.activePortal();
    if (portal) {
      try {
        await firstValueFrom(this.http.post(this.authUrl(portal, 'logout'), {}, { withCredentials: true }));
      } catch {
        /* logout is best-effort — clear locally regardless */
      }
    }
    this.store.clear();
    await this.router.navigate(['/login']);
  }

  /** App-bootstrap silent refresh: if a portal hint exists, try once to restore the session. Never throws. */
  async bootstrap(): Promise<void> {
    const portal = this.store.activePortal();
    if (!portal) {
      return;
    }
    try {
      await firstValueFrom(this.refresh(portal));
    } catch {
      this.store.clear();
    }
  }
}
