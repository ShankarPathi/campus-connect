import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Register } from './register';

describe('Register', () => {
  let mock: HttpTestingController;

  function setup() {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    mock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    return fixture;
  }
  afterEach(() => mock.verify());

  it('shows recruiter company fields only when Recruiter is selected', async () => {
    const fixture = setup();
    expect(fixture.nativeElement.textContent).not.toContain('Company name');

    fixture.componentInstance.portal.set('recruiter');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Company name');
  });

  it('registers a student and shows the check-your-email panel', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ collegeCode: 'iitb', email: 's@b.com', password: 'secret12', confirmPassword: 'secret12' });
    cmp.submit();

    const req = mock.expectOne('/api/student/auth/register');
    expect(req.request.body).toEqual({ collegeCode: 'iitb', email: 's@b.com', password: 'secret12' });
    req.flush({ email: 's@b.com', accountStatus: 'PENDING_VERIFICATION' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(cmp.done()?.email).toBe('s@b.com');
    expect(fixture.nativeElement.textContent).toContain('s@b.com');
    expect(fixture.nativeElement.textContent).toContain('sign in');
  });

  it('shows the "sign in now" panel when the account is auto-verified (ACTIVE)', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ collegeCode: 'iitb', email: 's@b.com', password: 'secret12', confirmPassword: 'secret12' });
    cmp.submit();

    const req = mock.expectOne('/api/student/auth/register');
    req.flush({ email: 's@b.com', accountStatus: 'ACTIVE' });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Account created!');
    expect(text).toContain('Sign in now');
    expect(text).not.toContain('verification link'); // no misleading "check your email" copy
  });

  it('sends recruiter company fields and shows approval copy', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    cmp.portal.set('recruiter');
    cmp.form.patchValue({ collegeCode: 'iitb', email: 'r@b.com', password: 'secret12', confirmPassword: 'secret12', companyName: 'Acme', industry: 'Tech' });
    cmp.submit();

    const req = mock.expectOne('/api/recruiter/auth/register');
    expect(req.request.body.companyName).toBe('Acme');
    expect(req.request.body.industry).toBe('Tech');
    expect(req.request.body.companyWebsite).toBeUndefined(); // empty optionals omitted (dropped on serialize)
    req.flush({ email: 'r@b.com', accountStatus: 'PENDING_VERIFICATION' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('College Admin approval');
  });

  it('blocks submit when passwords do not match', () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ collegeCode: 'iitb', email: 's@b.com', password: 'secret12', confirmPassword: 'different' });
    cmp.submit();
    mock.expectNone('/api/student/auth/register');
    expect(cmp.confirmError()).toBe('Passwords do not match.');
  });

  it('shows EMAIL_ALREADY_EXISTS inline on the email field', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ collegeCode: 'iitb', email: 'dupe@b.com', password: 'secret12', confirmPassword: 'secret12' });
    cmp.submit();
    mock.expectOne('/api/student/auth/register').flush(
      { success: false, error: { code: 'EMAIL_ALREADY_EXISTS', message: 'exists' } },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(cmp.err('email', 'Email')).toBe('An account with this email already exists.');
  });
});
