import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApplicationsService, DriveService, OffersService, ProfileService, StudentNotificationsService } from './student.services';

describe('student services', () => {
  let mock: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    mock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => mock.verify());

  it('DriveService.apply POSTs to the drive apply endpoint', () => {
    TestBed.inject(DriveService).apply('d1');
    const req = mock.expectOne('/api/student/drives/d1/apply');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'a1', driveId: 'd1', status: 'APPLIED', appliedAt: 't' });
  });

  it('ProfileService.uploadResume sends multipart with a "file" part', () => {
    const file = new File(['x'], 'cv.pdf', { type: 'application/pdf' });
    TestBed.inject(ProfileService).uploadResume(file);
    const req = mock.expectOne('/api/student/resume');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    expect((req.request.body as FormData).get('file')).toBeInstanceOf(File);
    req.flush({ hasResume: true });
  });

  it('ProfileService.submitProfile POSTs to /profile/submit', () => {
    TestBed.inject(ProfileService).submitProfile();
    const req = mock.expectOne('/api/student/profile/submit');
    expect(req.request.method).toBe('POST');
    req.flush({ completionPercent: 100 });
  });

  it('ApplicationsService.withdraw POSTs to the withdraw endpoint', () => {
    TestBed.inject(ApplicationsService).withdraw('a9');
    const req = mock.expectOne('/api/student/applications/a9/withdraw');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'a9', status: 'WITHDRAWN' });
  });

  it('OffersService.accept/decline hit the right endpoints', () => {
    const svc = TestBed.inject(OffersService);
    svc.accept('o1');
    mock.expectOne('/api/student/offers/o1/accept').flush({ id: 'o1', status: 'ACCEPTED' });
    svc.decline('o2');
    mock.expectOne('/api/student/offers/o2/decline').flush({ id: 'o2', status: 'DECLINED' });
  });

  it('StudentNotificationsService.markRead updates the unread signal', async () => {
    const svc = TestBed.inject(StudentNotificationsService);
    const p = svc.markRead('n1');
    mock.expectOne('/api/student/notifications/n1/read').flush({ unreadCount: 3 });
    await p;
    expect(svc.unreadCount()).toBe(3);
  });
});
