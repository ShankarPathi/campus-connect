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

// ── Story 9.3 auth-screen request/response types (mirror the Epic-2 DTOs) ──

/** Student self-registration (Story 2.1). */
export interface RegisterStudentRequest {
  collegeCode: string;
  email: string;
  password: string;
}

/** Recruiter self-registration (Story 2.2) — credentials + company details in one call. */
export interface RegisterRecruiterRequest {
  collegeCode: string;
  email: string;
  password: string;
  companyName: string;
  companyWebsite?: string;
  industry?: string;
  companyDescription?: string;
  recruiterDesignation?: string;
  contactPhone?: string;
}

/** Registration result (unwrapped) — never the id/hash/token. */
export interface RegisterResponse {
  email: string;
  accountStatus: string;
}

/** Request a password-reset OTP (Story 2.4). */
export interface ForgotPasswordRequest {
  collegeCode: string;
  email: string;
}

/** Set a new password using an emailed OTP (Story 2.4). */
export interface ResetPasswordRequest {
  collegeCode: string;
  email: string;
  otp: string;
  newPassword: string;
}
