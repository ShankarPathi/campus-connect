/** Auth contract types mirroring the Epic-2 backend (Story 9.2). */

export type Portal = 'student' | 'recruiter' | 'admin';
export type Role = 'STUDENT' | 'RECRUITER' | 'COLLEGE_ADMIN' | 'PLATFORM_ADMIN';

/** Login credentials — `collegeCode` is the tenant slug (email is unique per-tenant). */
export interface LoginRequest {
  collegeCode: string;
  email: string;
  password: string;
}

/** Login body (unwrapped from the ApiResponse envelope). The refresh token rides in an HttpOnly cookie. */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  role: Role;
}

/** Refresh body — a fresh access token (the rotated refresh token rides in the cookie). */
export interface RefreshResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

/** The access-token JWT payload (decoded client-side for display/routing only; the server is authoritative). */
export interface JwtClaims {
  sub: string; // userId
  role: Role;
  tenantId?: string; // omitted for PLATFORM_ADMIN
  exp?: number;
}

/** The portal a role belongs to (PLATFORM_ADMIN has no SPA portal in MVP). */
export const PORTAL_FOR_ROLE: Record<Role, Portal | null> = {
  STUDENT: 'student',
  RECRUITER: 'recruiter',
  COLLEGE_ADMIN: 'admin',
  PLATFORM_ADMIN: null,
};
