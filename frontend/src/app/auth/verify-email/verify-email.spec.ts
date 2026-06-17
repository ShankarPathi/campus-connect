import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { VerifyEmail } from './verify-email';

describe('VerifyEmail', () => {
  let mock: HttpTestingController;

  function setup(query: Record<string, string>) {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: (k: string) => query[k] ?? null } } } },
      ],
    });
    mock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(VerifyEmail);
    fixture.detectChanges();
    return fixture;
  }

  it('verifies a valid token and shows success', async () => {
    const fixture = setup({ token: 'tok123', portal: 'student' });
    const req = mock.expectOne((r) => r.url === '/api/student/auth/verify-email');
    expect(req.request.params.get('token')).toBe('tok123');
    req.flush(true);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('success');
    expect(fixture.nativeElement.textContent).toContain('verified');
  });

  it('shows the invalid state when the token is rejected', async () => {
    const fixture = setup({ token: 'used', portal: 'recruiter' });
    mock.expectOne((r) => r.url === '/api/recruiter/auth/verify-email').flush(
      { success: false, error: { code: 'EMAIL_VERIFY_TOKEN_INVALID', message: 'x' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await new Promise((resolve) => setTimeout(resolve)); // let the rejected verify promise settle
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('invalid');
  });

  it('shows the missing state when params are absent or portal is admin', () => {
    const fixture = setup({ token: 'x', portal: 'admin' });
    expect(fixture.componentInstance.state()).toBe('missing');
    mock.expectNone(() => true);
  });

  it('shows a transient error with a Retry that re-runs verification (not "invalid")', async () => {
    const fixture = setup({ token: 'tok', portal: 'student' });
    // First attempt fails transiently (server error) → error state, NOT invalid.
    mock.expectOne((r) => r.url === '/api/student/auth/verify-email').flush(
      { success: false, error: { code: 'INTERNAL', message: 'x' } },
      { status: 500, statusText: 'Server Error' },
    );
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('error');
    expect(fixture.nativeElement.textContent).toContain('Try again');

    // Retry succeeds the second time → success.
    fixture.componentInstance.retry();
    fixture.detectChanges();
    mock.expectOne((r) => r.url === '/api/student/auth/verify-email').flush(true);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('success');
  });
});
