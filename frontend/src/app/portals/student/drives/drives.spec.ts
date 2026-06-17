import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DrivesPage } from './drives';

const DRIVES = [
  { id: 'd1', companyName: 'TCS', role: 'SDE', packageLpa: 7, location: 'Pune', applyDeadline: null, status: 'PUBLISHED', group: 'ELIGIBLE', failedCriteria: null },
  { id: 'd2', companyName: 'Infy', role: 'SE', packageLpa: 6, location: null, applyDeadline: null, status: 'PUBLISHED', group: 'NOT_ELIGIBLE', failedCriteria: ['CGPA 6.4 — needs 7.0'] },
];

describe('DrivesPage', () => {
  let fixture: ComponentFixture<DrivesPage>;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(DrivesPage);
    mock = TestBed.inject(HttpTestingController);
  });

  // The load fires in the constructor; flush it then let the async chain settle (macrotask).
  async function load() {
    fixture.detectChanges();
    mock.expectOne('/api/student/drives').flush(DRIVES);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }

  it('groups drives into sections with counts and shows the Eligible section by default', async () => {
    await load();
    const cmp = fixture.componentInstance;
    expect(cmp.sections().map((s) => s.count)).toEqual([1, 0, 1, 0]);
    expect(cmp.visible().map((d) => d.id)).toEqual(['d1']); // Eligible active
  });

  it('shows the first failed reason on a NOT_ELIGIBLE card', async () => {
    await load();
    fixture.componentInstance.activeKey.set('NOT_ELIGIBLE');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('CGPA 6.4 — needs 7.0');
  });

  it('optimistically applies and POSTs to the apply endpoint', async () => {
    await load();
    const cmp = fixture.componentInstance;
    const p = cmp.apply(cmp.visible()[0]);
    // optimistic: d1 immediately moves out of ELIGIBLE
    expect(cmp.drives().find((d) => d.id === 'd1')?.group).toBe('APPLIED');
    const req = mock.expectOne('/api/student/drives/d1/apply');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'a1', driveId: 'd1', status: 'APPLIED', appliedAt: 't' });
    await p;
    expect(cmp.drives().find((d) => d.id === 'd1')?.group).toBe('APPLIED');
  });

  it('reverts the optimistic apply on a server error', async () => {
    await load();
    const cmp = fixture.componentInstance;
    const p = cmp.apply(cmp.visible()[0]);
    mock.expectOne('/api/student/drives/d1/apply').flush(
      { success: false, error: { code: 'DRIVE_DEADLINE_PASSED', message: 'x' } },
      { status: 409, statusText: 'Conflict' },
    );
    await p;
    expect(cmp.drives().find((d) => d.id === 'd1')?.group).toBe('ELIGIBLE'); // reverted
  });

  it('maps NOT_ELIGIBLE to danger eligibility rows in the detail modal', async () => {
    await load();
    const cmp = fixture.componentInstance;
    cmp.openDetail(cmp.drives().find((d) => d.id === 'd2')!);
    expect(cmp.checks()).toEqual([{ label: 'CGPA 6.4 — needs 7.0', passed: false, detail: 'CGPA 6.4 — needs 7.0' }]);
  });
});
