import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { DriveApprovalsPage } from './drives';

const DRIVES = [
  {
    id: 'd1',
    companyName: 'TCS',
    role: 'SDE',
    packageLpa: 7,
    location: 'Pune',
    openings: 5,
    applyDeadline: null,
    status: 'PENDING_APPROVAL',
    rejectionReason: null,
    eligibility: { branches: ['CSE'], minCgpa: 7, backlogPolicy: 'NO_BACKLOG', batch: '2026' },
  },
];

describe('DriveApprovalsPage', () => {
  let fixture: ComponentFixture<DriveApprovalsPage>;
  let page: DriveApprovalsPage;
  let mock: HttpTestingController;

  // The constructor fires `void this.load()`, so a GET is already pending; flush it then drain the microtask queue.
  async function settleLoad(rows: unknown[] = DRIVES): Promise<void> {
    mock.expectOne((r) => r.url === '/api/admin/drives').flush(rows);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DriveApprovalsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    mock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DriveApprovalsPage);
    page = fixture.componentInstance;
  });

  afterEach(() => mock.verify());

  it('loads and renders a drive card with the plain status label', async () => {
    fixture.detectChanges();
    await settleLoad();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('TCS');
    expect(text).toContain('SDE');
    expect(text).toContain('Pending approval');
    expect(page.rows().length).toBe(1);
  });

  it('Approve POSTs to /approve then refetches', async () => {
    fixture.detectChanges();
    await settleLoad();

    void page.approve(DRIVES[0] as never);
    await new Promise((r) => setTimeout(r));

    const approve = mock.expectOne('/api/admin/drives/d1/approve');
    expect(approve.request.method).toBe('POST');
    approve.flush(true);
    await new Promise((r) => setTimeout(r));

    // run() calls load() again → a fresh GET.
    mock.expectOne((r) => r.url === '/api/admin/drives').flush(DRIVES);
    await new Promise((r) => setTimeout(r));
  });

  it('reject with a reason POSTs the reason', async () => {
    fixture.detectChanges();
    await settleLoad();

    page.openReject(DRIVES[0] as never);
    page.rejectForm.setValue({ reason: 'Criteria too loose' });
    void page.doReject();
    await new Promise((r) => setTimeout(r));

    const reject = mock.expectOne('/api/admin/drives/d1/reject');
    expect(reject.request.method).toBe('POST');
    expect(reject.request.body).toEqual({ reason: 'Criteria too loose' });
    reject.flush(true);
    await new Promise((r) => setTimeout(r));

    mock.expectOne((r) => r.url === '/api/admin/drives').flush(DRIVES);
    await new Promise((r) => setTimeout(r));
  });

  it('editCriteria PATCHes /api/admin/drives/<id>', async () => {
    fixture.detectChanges();
    await settleLoad();

    page.openEdit(DRIVES[0] as never);
    page.editForm.setValue({ branches: 'CSE, ECE', minCgpa: '8', batch: '2027' });
    void page.doEdit();
    await new Promise((r) => setTimeout(r));

    const edit = mock.expectOne('/api/admin/drives/d1');
    expect(edit.request.method).toBe('PATCH');
    expect(edit.request.body).toEqual({ branches: ['CSE', 'ECE'], minCgpa: 8, batch: '2027' });
    edit.flush(true);
    await new Promise((r) => setTimeout(r));

    mock.expectOne((r) => r.url === '/api/admin/drives').flush(DRIVES);
    await new Promise((r) => setTimeout(r));
  });
});
