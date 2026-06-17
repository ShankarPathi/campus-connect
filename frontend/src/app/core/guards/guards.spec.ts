import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlSegment, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { requireRole } from './role.guard';
import { AuthStore } from '../auth/auth.store';
import { AuthService } from '../auth/auth.service';

function jwt(claims: object): string {
  const b64 = (o: object) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.sig`;
}

describe('authGuard', () => {
  let store: AuthStore;
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { bootstrap: async () => {} } }],
    });
    store = TestBed.inject(AuthStore);
  });

  it('allows an authenticated session', async () => {
    store.setSession(jwt({ sub: 'u1', role: 'STUDENT' }), 'student');
    const result = await TestBed.runInInjectionContext(() =>
      authGuard({} as never, [new UrlSegment('student', {})], {} as never),
    );
    expect(result).toBe(true);
  });

  it('redirects to /login when there is no session (and bootstrap cannot restore one)', async () => {
    const result = await TestBed.runInInjectionContext(() =>
      authGuard({} as never, [new UrlSegment('student', {})], {} as never),
    );
    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toContain('/login');
  });
});

describe('requireRole', () => {
  let store: AuthStore;
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    store = TestBed.inject(AuthStore);
  });

  it('allows a matching role', () => {
    store.setSession(jwt({ sub: 'u1', role: 'STUDENT' }), 'student');
    const result = TestBed.runInInjectionContext(() => requireRole('STUDENT')({} as never, [], {} as never));
    expect(result).toBe(true);
  });

  it('redirects a mismatched role to their own portal', () => {
    store.setSession(jwt({ sub: 'u1', role: 'STUDENT' }), 'student');
    const result = TestBed.runInInjectionContext(() => requireRole('COLLEGE_ADMIN')({} as never, [], {} as never));
    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toContain('/student');
  });
});
