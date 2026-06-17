import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Login } from './login';

function jwt(claims: object): string {
  const b64 = (o: object) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.sig`;
}
const okBody = (role = 'STUDENT') => ({ accessToken: jwt({ sub: 'u1', role, tenantId: 't1' }), tokenType: 'Bearer', expiresInSeconds: 900, role });

describe('Login', () => {
  let mock: HttpTestingController;
  let returnUrl: string | null;
  let portalParam: string | null;

  function setup(portal: string | null = null) {
    localStorage.clear();
    returnUrl = null;
    portalParam = portal;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: (k: string) => (k === 'portal' ? portalParam : returnUrl) } } },
        },
      ],
    });
    mock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return fixture;
  }
  afterEach(() => mock.verify());

  it('submits valid credentials and navigates to the portal home', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    const nav = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    cmp.form.setValue({ collegeCode: 'iitb', email: 'a@b.com', password: 'secret12' });
    cmp.submit();
    const req = mock.expectOne('/api/student/auth/login');
    expect(req.request.body).toEqual({ collegeCode: 'iitb', email: 'a@b.com', password: 'secret12' });
    req.flush(okBody());
    await fixture.whenStable();

    expect(nav).toHaveBeenCalledWith('/student');
  });

  it('honors the returnUrl query param', async () => {
    const fixture = setup();
    returnUrl = '/student/drives/42';
    const cmp = fixture.componentInstance;
    const nav = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    cmp.form.setValue({ collegeCode: 'iitb', email: 'a@b.com', password: 'secret12' });
    cmp.submit();
    mock.expectOne('/api/student/auth/login').flush(okBody());
    await fixture.whenStable();

    expect(nav).toHaveBeenCalledWith('/student/drives/42');
  });

  it('shows a plain-language banner on INVALID_CREDENTIALS (raw 401 envelope)', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    cmp.form.setValue({ collegeCode: 'iitb', email: 'a@b.com', password: 'wrongpw1' });
    cmp.submit();
    mock.expectOne('/api/student/auth/login').flush(
      { success: false, error: { code: 'INVALID_CREDENTIALS', message: 'bad' } },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(cmp.formError()).toBe('The email or password is incorrect.');
    const banner = fixture.nativeElement.querySelector('.form-error') as HTMLElement;
    expect(banner.textContent?.trim()).toBe('The email or password is incorrect.');
  });

  it('maps a VALIDATION_ERROR response to inline field errors', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    cmp.form.setValue({ collegeCode: 'iitb', email: 'a@b.com', password: 'secret12' });
    cmp.submit();
    mock.expectOne('/api/student/auth/login').flush(
      { success: false, error: { code: 'VALIDATION_ERROR', message: 'Validation failed', fields: { collegeCode: 'must not be blank' } } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(cmp.err('collegeCode', 'College code')).toBe('must not be blank');
  });

  it('does not submit when the form is invalid', () => {
    const fixture = setup();
    fixture.componentInstance.submit();
    mock.expectNone('/api/student/auth/login');
  });

  it('seeds the portal from the ?portal= query param', () => {
    const fixture = setup('recruiter');
    expect(fixture.componentInstance.portal()).toBe('recruiter');
  });

  it('clears a stale server field error once the user edits (review patch)', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    cmp.form.setValue({ collegeCode: 'iitb', email: 'a@b.com', password: 'secret12' });
    cmp.submit();
    mock.expectOne('/api/student/auth/login').flush(
      { success: false, error: { code: 'VALIDATION_ERROR', message: 'x', fields: { collegeCode: 'must not be blank' } } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect(cmp.err('collegeCode', 'College code')).toBe('must not be blank');

    cmp.form.patchValue({ collegeCode: 'iitX' }); // user edits → server error clears
    expect(cmp.err('collegeCode', 'College code')).toBe('');
  });

  it('trims a pasted college code / email before submit (review patch)', () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    cmp.form.setValue({ collegeCode: '  iitb ', email: ' a@b.com ', password: 'secret12' });
    cmp.submit();
    const req = mock.expectOne('/api/student/auth/login');
    expect(req.request.body.collegeCode).toBe('iitb');
    expect(req.request.body.email).toBe('a@b.com');
    req.flush(okBody());
  });

  it('hides the Create account link when Admin is selected', async () => {
    const fixture = setup();
    fixture.componentInstance.portal.set('admin');
    fixture.detectChanges();
    await fixture.whenStable();
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')).map((a) => (a as HTMLElement).textContent ?? '');
    expect(links.some((t) => t.includes('Create an account'))).toBe(false);
    expect(links.some((t) => t.includes('Forgot password'))).toBe(true);
  });
});
