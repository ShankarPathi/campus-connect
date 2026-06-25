import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProfilePage } from './profile';

const PROFILE = (over: Record<string, unknown> = {}) => ({
  studentId: 's1',
  rollNumber: 'R1',
  batch: '2026',
  personal: { fullName: 'Anjali', phone: '9876543210', gender: null, dateOfBirth: null, address: null },
  academic: { branch: 'CSE', cgpa: 8.1, activeBacklogs: 0 },
  placement: { skills: ['Java', 'SQL'], expectedRole: null, about: null },
  profileApprovalStatus: 'DRAFT',
  rejectionReason: null,
  isPlaced: false,
  isLocked: false,
  completionPercent: 60,
  ...over,
});

describe('ProfilePage', () => {
  let fixture: ComponentFixture<ProfilePage>;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(ProfilePage);
    mock = TestBed.inject(HttpTestingController);
  });

  async function load(profileOver: Record<string, unknown> = {}, resume: Record<string, unknown> = { hasResume: false }) {
    fixture.detectChanges();
    mock.expectOne('/api/student/profile').flush(PROFILE(profileOver));
    mock.expectOne('/api/student/resume').flush(resume);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }

  it('loads the profile into the form + ring and shows the status pill', async () => {
    await load();
    expect(fixture.componentInstance.completion()).toBe(60);
    expect(fixture.componentInstance.form.get('placement.skills')?.value).toBe('Java, SQL');
    expect(fixture.nativeElement.textContent).toContain('Draft');
  });

  it('save draft PUTs the request and shows the new completion', async () => {
    await load();
    const p = fixture.componentInstance.save();
    const req = mock.expectOne('/api/student/profile');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.placement.skills).toEqual(['Java', 'SQL']);
    req.flush(PROFILE({ completionPercent: 80 }));
    await p;
    expect(fixture.componentInstance.completion()).toBe(80);
  });

  it('submit POSTs to /profile/submit', async () => {
    await load();
    const p = fixture.componentInstance.submit();
    const req = mock.expectOne('/api/student/profile/submit');
    expect(req.request.method).toBe('POST');
    req.flush(PROFILE({ profileApprovalStatus: 'PENDING_APPROVAL', completionPercent: 100 }));
    await p;
    expect(fixture.componentInstance.profile()?.profileApprovalStatus).toBe('PENDING_APPROVAL');
  });

  it('renders read-only with a lock banner when isLocked', async () => {
    await load({ isLocked: true });
    expect(fixture.componentInstance.form.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('locked');
  });

  it('blocks save when CGPA is non-numeric (no silent NaN→null coercion)', async () => {
    await load();
    const cmp = fixture.componentInstance;
    cmp.form.get('academic.cgpa')?.setValue('abc');
    cmp.save();
    mock.expectNone('/api/student/profile');
    expect(cmp.err('academic.cgpa', 'CGPA')).toContain('must be a number');
  });

  it('rejects a non-PDF resume client-side without hitting the server', async () => {
    await load();
    const cmp = fixture.componentInstance;
    const file = new File(['x'], 'cv.txt', { type: 'text/plain' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    await cmp.onFile({ target: input } as unknown as Event);
    expect(cmp.resumeError()).toContain('PDF');
    mock.expectNone('/api/student/resume');
  });
});
