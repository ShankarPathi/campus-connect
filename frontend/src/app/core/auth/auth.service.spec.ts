import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService (Story 9.3 methods)', () => {
  let svc: AuthService;
  let mock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    svc = TestBed.inject(AuthService);
    mock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => mock.verify());

  it('register POSTs to the portal register endpoint', () => {
    svc.register('recruiter', { collegeCode: 'c', email: 'a@b.com', password: 'pw', companyName: 'Acme' });
    const req = mock.expectOne('/api/recruiter/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.companyName).toBe('Acme');
    req.flush({ email: 'a@b.com', accountStatus: 'PENDING_VERIFICATION' });
  });

  it('verifyEmail GETs the verify endpoint with the token param', () => {
    svc.verifyEmail('student', 'tok123');
    const req = mock.expectOne((r) => r.url === '/api/student/auth/verify-email');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('token')).toBe('tok123');
    req.flush(true);
  });

  it('forgotPassword POSTs to password/forgot', () => {
    svc.forgotPassword('student', { collegeCode: 'c', email: 'a@b.com' });
    const req = mock.expectOne('/api/student/auth/password/forgot');
    expect(req.request.method).toBe('POST');
    req.flush(true);
  });

  it('resetPassword POSTs to password/reset', () => {
    svc.resetPassword('admin', { collegeCode: 'c', email: 'a@b.com', otp: '123456', newPassword: 'newpass12' });
    const req = mock.expectOne('/api/admin/auth/password/reset');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.otp).toBe('123456');
    req.flush(true);
  });
});
