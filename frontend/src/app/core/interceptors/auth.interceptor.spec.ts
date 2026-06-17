import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from '../auth/auth.store';

function jwt(claims: object): string {
  const b64 = (o: object) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.sig`;
}

describe('authInterceptor', () => {
  let http: HttpClient;
  let mock: HttpTestingController;
  let store: AuthStore;
  const tokenA = jwt({ sub: 'u1', role: 'STUDENT', tenantId: 't1' });

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    mock = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
  });
  afterEach(() => mock.verify());

  it('attaches the Bearer token to /api requests', () => {
    store.setSession(tokenA, 'student');
    http.get('/api/student/profiles').subscribe();
    const req = mock.expectOne('/api/student/profiles');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${tokenA}`);
    req.flush({});
  });

  it('does NOT attach the token to /api/*/auth/** endpoints', () => {
    store.setSession(tokenA, 'student');
    http.post('/api/student/auth/login', {}).subscribe();
    const req = mock.expectOne('/api/student/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('on a 401 refreshes once and retries the original request with the new token', () => {
    store.setSession(tokenA, 'student');
    let ok: unknown;
    http.get('/api/student/profiles').subscribe((r) => (ok = r));

    mock.expectOne('/api/student/profiles').flush(null, { status: 401, statusText: 'Unauthorized' });

    // single-flight refresh against the active portal
    const refresh = mock.expectOne('/api/student/auth/refresh');
    refresh.flush({ accessToken: 'TOKEN_B', tokenType: 'Bearer', expiresInSeconds: 900 });

    // original request retried with the new token
    const retry = mock.expectOne('/api/student/profiles');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer TOKEN_B');
    retry.flush({ ok: true });

    expect(ok).toEqual({ ok: true });
  });

  it('coalesces two concurrent 401s into a single refresh (single-flight)', () => {
    store.setSession(tokenA, 'student');
    const results: unknown[] = [];
    http.get('/api/student/profiles').subscribe((r) => results.push(r));
    http.get('/api/student/drives').subscribe((r) => results.push(r));

    // both original requests 401
    mock.expectOne('/api/student/profiles').flush(null, { status: 401, statusText: 'Unauthorized' });
    mock.expectOne('/api/student/drives').flush(null, { status: 401, statusText: 'Unauthorized' });

    // exactly ONE refresh despite two 401s
    mock.expectOne('/api/student/auth/refresh').flush({ accessToken: 'TOKEN_B', tokenType: 'Bearer', expiresInSeconds: 900 });

    // both originals retried with the new token
    mock.expectOne('/api/student/profiles').flush({ a: 1 });
    mock.expectOne('/api/student/drives').flush({ b: 2 });

    expect(results).toEqual([{ a: 1 }, { b: 2 }]);
  });
});
