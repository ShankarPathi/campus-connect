import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RecruiterOffers } from './offers';

const applicant = (over = {}) => ({
  applicationId: 'a1', status: 'SELECTED', appliedAt: 't', fullName: 'Anjali', phone: null,
  rollNumber: 'R1', batch: '2026', branch: 'CSE', cgpa: 8, activeBacklogs: 0, skills: [],
  expectedRole: null, about: null, isPlaced: false, ...over,
});
const pageOf = (items: unknown[]) => ({ items, totalCount: items.length, page: 0, pageSize: 200, totalPages: 1 });

describe('RecruiterOffers', () => {
  let fixture: ComponentFixture<RecruiterOffers>;
  let mock: HttpTestingController;
  const LIST = '/api/recruiter/drives/d1/applicants';

  async function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(RecruiterOffers);
    mock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('driveId', 'd1');
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r)); // queueMicrotask load
    mock.expectOne((r) => r.method === 'GET' && r.url === LIST).flush(pageOf([applicant()]));
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }
  afterEach(() => mock.verify());

  it('lists selected applicants with a Release offer button', async () => {
    await setup();
    expect(fixture.componentInstance.rows().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Anjali');
    expect(fixture.nativeElement.textContent).toContain('Release offer');
  });

  it('release() POSTs a multipart offer then refetches', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.openRelease(cmp.rows()[0]);
    cmp.form.setValue({ role: 'SDE', ctc: '12', joiningDate: '2026-07-01T00:00:00Z', acceptanceDeadline: '2026-06-25T00:00:00Z' });
    cmp.onFile({ target: { files: [new File(['x'], 'o.pdf', { type: 'application/pdf' })] } } as unknown as Event);
    cmp.release();
    const post = mock.expectOne((r) => r.method === 'POST' && r.url === LIST + '/a1/offer');
    const body = post.request.body;
    expect(body instanceof FormData).toBe(true);
    expect((body as FormData).get('file') instanceof File).toBe(true);
    post.flush({ id: 'o1', applicationId: 'a1', status: 'PENDING' });
    await new Promise((r) => setTimeout(r));
    // refetch after release
    mock.expectOne((r) => r.method === 'GET' && r.url === LIST).flush(pageOf([]));
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    expect(cmp.rows().length).toBe(0);
  });
});
