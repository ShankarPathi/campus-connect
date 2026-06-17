import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { ResetPassword } from './reset-password';

describe('ResetPassword', () => {
  let mock: HttpTestingController;

  function setup(query: Record<string, string> = {}) {
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
    const fixture = TestBed.createComponent(ResetPassword);
    fixture.detectChanges();
    return fixture;
  }
  afterEach(() => mock.verify());

  it('prefills portal/college/email from the query and shows the sent notice', () => {
    const fixture = setup({ portal: 'recruiter', collegeCode: 'iitb', email: 'a@b.com', sent: '1' });
    const cmp = fixture.componentInstance;
    expect(cmp.portal()).toBe('recruiter');
    expect(cmp.form.getRawValue().collegeCode).toBe('iitb');
    expect(cmp.form.getRawValue().email).toBe('a@b.com');
    expect(cmp.sent()).toBe(true);
  });

  it('resets the password and shows the confirmation panel with a Sign in link to /login (review patch)', async () => {
    const fixture = setup({ portal: 'student', collegeCode: 'iitb', email: 'a@b.com' });
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ otp: '123456', newPassword: 'newpass12', confirmPassword: 'newpass12' });
    cmp.submit();

    const req = mock.expectOne('/api/student/auth/password/reset');
    expect(req.request.body).toEqual({ collegeCode: 'iitb', email: 'a@b.com', otp: '123456', newPassword: 'newpass12' });
    req.flush(true);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(cmp.done()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('password is updated');
    const signIn = Array.from(fixture.nativeElement.querySelectorAll('.done a')).find((a) =>
      (a as HTMLElement).textContent?.includes('Sign in'),
    ) as HTMLAnchorElement | undefined;
    expect(signIn?.getAttribute('href')).toContain('/login');
  });

  it('trims a pasted OTP with surrounding spaces before submit (review patch)', () => {
    const fixture = setup({ collegeCode: 'iitb', email: 'a@b.com' });
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ otp: ' 123456 ', newPassword: 'newpass12', confirmPassword: 'newpass12' });
    cmp.submit();
    const req = mock.expectOne('/api/student/auth/password/reset');
    expect(req.request.body.otp).toBe('123456');
    req.flush(true);
  });

  it('blocks submit when passwords do not match', () => {
    const fixture = setup({ collegeCode: 'iitb', email: 'a@b.com' });
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ otp: '123456', newPassword: 'newpass12', confirmPassword: 'different1' });
    cmp.submit();
    mock.expectNone('/api/student/auth/password/reset');
    expect(cmp.confirmError()).toBe('Passwords do not match.');
  });

  it('shows OTP_INVALID inline on the code field', async () => {
    const fixture = setup({ collegeCode: 'iitb', email: 'a@b.com' });
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ otp: '000000', newPassword: 'newpass12', confirmPassword: 'newpass12' });
    cmp.submit();
    mock.expectOne('/api/student/auth/password/reset').flush(
      { success: false, error: { code: 'OTP_INVALID', message: 'x' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect(cmp.err('otp', 'Code')).toBe("That code isn't right — check your email.");
  });

  it('shows OTP_EXPIRED as a form banner', async () => {
    const fixture = setup({ collegeCode: 'iitb', email: 'a@b.com' });
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ otp: '123456', newPassword: 'newpass12', confirmPassword: 'newpass12' });
    cmp.submit();
    mock.expectOne('/api/student/auth/password/reset').flush(
      { success: false, error: { code: 'OTP_EXPIRED', message: 'x' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect(cmp.formError()).toBe('That code has expired — request a new one.');
  });
});
