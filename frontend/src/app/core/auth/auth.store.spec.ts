import { TestBed } from '@angular/core/testing';
import { AuthStore, decodeJwt } from './auth.store';

function jwt(claims: object): string {
  const b64 = (o: object) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.sig`;
}

describe('decodeJwt', () => {
  it('decodes the base64url payload claims', () => {
    const c = decodeJwt(jwt({ sub: 'u1', role: 'STUDENT', tenantId: 't1' }));
    expect(c).toEqual({ sub: 'u1', role: 'STUDENT', tenantId: 't1' });
  });
  it('returns null on a malformed token', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull();
    expect(decodeJwt('')).toBeNull();
  });
});

describe('AuthStore', () => {
  let store: AuthStore;
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    store = TestBed.inject(AuthStore);
  });

  it('starts unauthenticated', () => {
    expect(store.isAuthenticated()).toBe(false);
    expect(store.role()).toBeNull();
  });

  it('setSession decodes claims, persists the portal hint, and authenticates', () => {
    store.setSession(jwt({ sub: 'u1', role: 'STUDENT', tenantId: 't1' }), 'student');
    expect(store.isAuthenticated()).toBe(true);
    expect(store.role()).toBe('STUDENT');
    expect(store.userId()).toBe('u1');
    expect(store.tenantId()).toBe('t1');
    expect(store.tenantName()).toBe('t1'); // interim
    expect(store.activePortal()).toBe('student');
    expect(localStorage.getItem('cc.portal')).toBe('student');
  });

  it('clear wipes the session and the hint', () => {
    store.setSession(jwt({ sub: 'u1', role: 'STUDENT' }), 'student');
    store.clear();
    expect(store.isAuthenticated()).toBe(false);
    expect(store.activePortal()).toBeNull();
    expect(localStorage.getItem('cc.portal')).toBeNull();
  });
});
