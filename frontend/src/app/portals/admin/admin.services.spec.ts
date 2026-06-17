import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  DriveApprovalService,
  EligibilityPolicyService,
  PlacementService,
  ProfileApprovalService,
  RecruiterApprovalService,
  ReportService,
} from './admin.services';

describe('admin services', () => {
  let mock: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    mock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => mock.verify());

  it('ProfileApprovalService.list sends the status filter', () => {
    TestBed.inject(ProfileApprovalService).list('PENDING_APPROVAL');
    const req = mock.expectOne((r) => r.url === '/api/admin/profiles');
    expect(req.request.params.get('status')).toBe('PENDING_APPROVAL');
    req.flush([]);
  });

  it('ProfileApprovalService.reject POSTs the reason', () => {
    TestBed.inject(ProfileApprovalService).reject('s1', 'CGPA mismatch');
    const req = mock.expectOne('/api/admin/profiles/s1/reject');
    expect(req.request.body).toEqual({ reason: 'CGPA mismatch' });
    req.flush(true);
  });

  it('RecruiterApprovalService.approve POSTs to approve', () => {
    TestBed.inject(RecruiterApprovalService).approve('u1');
    mock.expectOne('/api/admin/recruiters/u1/approve').flush(true);
  });

  it('DriveApprovalService.editCriteria PATCHes the criteria', () => {
    TestBed.inject(DriveApprovalService).editCriteria('d1', { branches: ['CSE'], minCgpa: 7, batch: '2026' });
    const req = mock.expectOne('/api/admin/drives/d1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.minCgpa).toBe(7);
    req.flush(true);
  });

  it('PlacementService.confirm POSTs to confirm', () => {
    TestBed.inject(PlacementService).confirm('p1');
    mock.expectOne('/api/admin/placements/p1/confirm').flush({ id: 'p1', status: 'OFFICIALLY_PLACED' });
  });

  it('EligibilityPolicyService.update PUTs the two editable fields', () => {
    TestBed.inject(EligibilityPolicyService).update({ minCgpaFloor: 6, reapplyPackageThresholdLpa: 10 });
    const req = mock.expectOne('/api/admin/eligibility-policy');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ minCgpaFloor: 6, reapplyPackageThresholdLpa: 10 });
    req.flush({ minCgpaFloor: 6, placedStudentsMayApply: true, reapplyPackageThresholdLpa: 10 });
  });

  it('ReportService.exportCsv requests text (non-JSON)', () => {
    TestBed.inject(ReportService).exportCsv();
    const req = mock.expectOne('/api/admin/reports/placements/export');
    expect(req.request.responseType).toBe('text');
    req.flush('branch,placed\nCSE,10');
  });
});
