import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { ForgotPassword } from './forgot-password';

describe('ForgotPassword', () => {
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
    const fixture = TestBed.createComponent(ForgotPassword);
    fixture.detectChanges();
    return fixture;
  }
  afterEach(() => mock.verify());

  it('seeds the portal from the query param', () => {
    const fixture = setup({ portal: 'recruiter' });
    expect(fixture.componentInstance.portal()).toBe('recruiter');
  });

  it('requests the code and advances to /reset-password with prefilled params', async () => {
    const fixture = setup();
    const cmp = fixture.componentInstance;
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    cmp.form.setValue({ collegeCode: 'iitb', email: 'a@b.com' });
    cmp.submit();
    const req = mock.expectOne('/api/student/auth/password/forgot');
    expect(req.request.body).toEqual({ collegeCode: 'iitb', email: 'a@b.com' });
    req.flush(true);
    await fixture.whenStable();

    expect(nav).toHaveBeenCalledWith(['/reset-password'], {
      queryParams: { portal: 'student', collegeCode: 'iitb', email: 'a@b.com', sent: '1' },
    });
  });

  it('does not submit an invalid form', () => {
    const fixture = setup();
    fixture.componentInstance.submit();
    mock.expectNone('/api/student/auth/password/forgot');
  });
});
