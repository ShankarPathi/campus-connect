import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApplicantService, DriveService, OfferService, RoundService } from './recruiter.services';

describe('recruiter services', () => {
  let mock: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    mock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => mock.verify());

  it('DriveService.update PUTs the full DriveRequest', () => {
    const body = { role: 'SDE', packageLpa: 12, location: 'Pune', eligibility: { branches: ['CSE'], minCgpa: 7, backlogPolicy: 'NO_BACKLOG' as const, batch: '2026' }, openings: 5, applyDeadline: null };
    TestBed.inject(DriveService).update('d1', body);
    const req = mock.expectOne('/api/recruiter/drives/d1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.openings).toBe(5);
    req.flush({ id: 'd1', status: 'DRAFT' });
  });

  it('ApplicantService.list sends filter/sort/page query params', () => {
    TestBed.inject(ApplicantService).list('d1', { status: ['SHORTLISTED'], search: 'anj', sortBy: 'cgpa', sortDir: 'desc', page: 1, pageSize: 20 });
    const req = mock.expectOne((r) => r.url === '/api/recruiter/drives/d1/applicants');
    expect(req.request.params.getAll('status')).toEqual(['SHORTLISTED']);
    expect(req.request.params.get('search')).toBe('anj');
    expect(req.request.params.get('sortBy')).toBe('cgpa');
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ items: [], totalCount: 0, page: 1, pageSize: 20, totalPages: 0 });
  });

  it('ApplicantService.shortlist/select POST {applicationIds}', () => {
    const svc = TestBed.inject(ApplicantService);
    svc.shortlist('d1', ['a1', 'a2']);
    const s = mock.expectOne('/api/recruiter/drives/d1/applicants/shortlist');
    expect(s.request.body).toEqual({ applicationIds: ['a1', 'a2'] });
    s.flush({ succeeded: ['a1', 'a2'], failed: [], succeededCount: 2, failedCount: 0 });
    svc.select('d1', ['a1']);
    mock.expectOne('/api/recruiter/drives/d1/applicants/select').flush({ succeeded: ['a1'], failed: [], succeededCount: 1, failedCount: 0, selectedTotal: 1, openings: 1, warning: null });
  });

  it('RoundService.recordResults POSTs results to the round', () => {
    TestBed.inject(RoundService).recordResults('d1', 2, { results: [{ applicationId: 'a1', result: 'PASS' }] });
    const req = mock.expectOne('/api/recruiter/drives/d1/rounds/2/results');
    expect(req.request.body.results[0].result).toBe('PASS');
    req.flush({ succeeded: ['a1'], failed: [], succeededCount: 1, failedCount: 0 });
  });

  it('OfferService.release sends multipart with file + data parts', () => {
    const file = new File(['x'], 'offer.pdf', { type: 'application/pdf' });
    TestBed.inject(OfferService).release('d1', 'a1', { role: 'SDE', ctc: 12, joiningDate: '2026-07-01T00:00:00Z', acceptanceDeadline: '2026-06-25T00:00:00Z' }, file);
    const req = mock.expectOne('/api/recruiter/drives/d1/applicants/a1/offer');
    expect(req.request.method).toBe('POST');
    const form = req.request.body as FormData;
    expect(form.get('file')).toBeInstanceOf(File);
    expect(form.get('data')).toBeInstanceOf(Blob);
    req.flush({ id: 'o1', applicationId: 'a1', status: 'PENDING' });
  });
});
