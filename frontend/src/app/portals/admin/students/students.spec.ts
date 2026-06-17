import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { StudentApprovalsPage } from './students';

const PROFILE = { studentId: 's1', rollNumber: 'R1', fullName: 'Anjali', branch: 'CSE', cgpa: 8.1, activeBacklogs: 0, batch: '2026', completionPercent: 90, isLocked: false };

describe('StudentApprovalsPage', () => {
  let fixture: ComponentFixture<StudentApprovalsPage>;
  let mock: HttpTestingController;
  const URL = '/api/admin/profiles';

  async function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(StudentApprovalsPage);
    mock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const req = mock.expectOne((r) => r.url === URL);
    expect(req.request.params.get('status')).toBe('PENDING_APPROVAL');
    req.flush([PROFILE]);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }
  afterEach(() => mock.verify());

  it('loads the pending profiles', async () => {
    await setup();
    expect(fixture.componentInstance.rows().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Anjali');
  });

  it('approve POSTs then refetches', async () => {
    await setup();
    fixture.componentInstance.approve(PROFILE);
    mock.expectOne(URL + '/s1/approve').flush(true);
    await new Promise((r) => setTimeout(r));
    mock.expectOne((r) => r.url === URL).flush([]);
  });

  it('reject blocks a whitespace-only reason (review patch)', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.openReject(PROFILE);
    cmp.rejectForm.setValue({ reason: '   ' });
    cmp.doReject();
    mock.expectNone(URL + '/s1/reject');
  });

  it('edit blocks a non-numeric CGPA (no silent NaN→null wipe) (review patch)', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.openEdit(PROFILE);
    cmp.editForm.patchValue({ cgpa: 'abc' });
    cmp.doEdit();
    mock.expectNone((r) => r.url === URL + '/s1' && r.method === 'PATCH');
  });

  it('discards a stale filter response that resolves after a newer one (race guard, review patch)', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.setStatus('APPROVED'); // request A
    cmp.setStatus('REJECTED'); // request B (newer)

    const reqA = mock.expectOne((r) => r.url === URL && r.params.get('status') === 'APPROVED');
    const reqB = mock.expectOne((r) => r.url === URL && r.params.get('status') === 'REJECTED');

    // The newer request resolves first, then the stale earlier one resolves late.
    reqB.flush([{ ...PROFILE, studentId: 'rej' }]);
    await new Promise((r) => setTimeout(r));
    reqA.flush([{ ...PROFILE, studentId: 'app' }]);
    await new Promise((r) => setTimeout(r));

    expect(cmp.rows().map((r) => r.studentId)).toEqual(['rej']); // B won; A was discarded
  });

  it('reject requires a reason before POSTing', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.openReject(PROFILE);
    cmp.doReject(); // empty reason → blocked
    mock.expectNone(URL + '/s1/reject');
    cmp.rejectForm.setValue({ reason: 'CGPA mismatch' });
    cmp.doReject();
    const req = mock.expectOne(URL + '/s1/reject');
    expect(req.request.body).toEqual({ reason: 'CGPA mismatch' });
    req.flush(true);
    await new Promise((r) => setTimeout(r));
    mock.expectOne((r) => r.url === URL).flush([]);
  });
});
