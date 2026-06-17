import { computed, Injectable, signal } from '@angular/core';
import { JwtClaims, Portal, Role } from './auth.models';

const PORTAL_HINT_KEY = 'cc.portal';

/**
 * In-memory session state (Story 9.2). The access token lives **only** in memory (never localStorage —
 * XSS-safer; the backend's documented design). Only the non-sensitive `activePortal` hint is persisted so a
 * cold reload of a deep link knows which portal's `/refresh` to call. Claims are read by decoding the JWT
 * payload (display/routing only).
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _accessToken = signal<string | null>(null);
  private readonly _claims = signal<JwtClaims | null>(null);
  private readonly _activePortal = signal<Portal | null>(restorePortalHint());

  readonly accessToken = this._accessToken.asReadonly();
  readonly claims = this._claims.asReadonly();
  readonly activePortal = this._activePortal.asReadonly();

  readonly isAuthenticated = computed(() => this._accessToken() !== null);
  readonly role = computed<Role | null>(() => this._claims()?.role ?? null);
  readonly userId = computed<string | null>(() => this._claims()?.sub ?? null);
  readonly tenantId = computed<string | null>(() => this._claims()?.tenantId ?? null);
  /** Interim: no API exposes the college name yet, so the topbar shows the tenantId (see story Dev Notes E). */
  readonly tenantName = computed<string | null>(() => this._claims()?.tenantId ?? null);

  /** Set a full session after login: decode the token and remember the portal (persist the hint). */
  setSession(accessToken: string, portal: Portal): void {
    this._accessToken.set(accessToken);
    this._claims.set(decodeJwt(accessToken));
    this._activePortal.set(portal);
    try {
      localStorage.setItem(PORTAL_HINT_KEY, portal);
    } catch {
      /* storage unavailable — the in-memory portal still works for this tab */
    }
  }

  /** Replace just the access token (after a refresh) — keeps the active portal. */
  setAccessToken(accessToken: string): void {
    this._accessToken.set(accessToken);
    this._claims.set(decodeJwt(accessToken));
  }

  /** Wipe the session and the portal hint. */
  clear(): void {
    this._accessToken.set(null);
    this._claims.set(null);
    this._activePortal.set(null);
    try {
      localStorage.removeItem(PORTAL_HINT_KEY);
    } catch {
      /* ignore */
    }
  }
}

/** Decode a JWT payload (base64url) — returns null on any malformed token. No signature check (server-authoritative). */
export function decodeJwt(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) {
      return null;
    }
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const binary = atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '='));
    // UTF-8-safe: atob yields latin1 bytes — decode them as UTF-8 so a future unicode claim isn't mangled
    const json = new TextDecoder().decode(Uint8Array.from(binary, (c) => c.charCodeAt(0)));
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}

function restorePortalHint(): Portal | null {
  try {
    const v = localStorage.getItem(PORTAL_HINT_KEY);
    return v === 'student' || v === 'recruiter' || v === 'admin' ? v : null;
  } catch {
    return null;
  }
}
