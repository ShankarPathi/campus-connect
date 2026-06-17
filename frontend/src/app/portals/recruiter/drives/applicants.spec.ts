import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RecruiterApplicants } from './applicants';

const applicant = (id: string, over = {}) => ({
  applicationId: id, status: 'APPLIED', appliedAt: 't', fullName: 'Anjali', phone: null,
  rollNumber: 'R1', batch: '2026', branch: 'CSE', cgpa: 8, activeBacklogs: 0, skills: [], expectedRole: null, about: null, isPlaced: false, ...over,
});
const pageOf = (items: unknown[]) => ({ items, totalCount: items.length, page: 0, pageSize: 20, totalPages: 1 });

describe('RecruiterApplicants', () => {
  let fixture: ComponentFixture<RecruiterApplicants>;
  let mock: HttpTestingController;
  const URL = '/api/recruiter/drives/d1/applicants';

  async function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(RecruiterApplicants);
    mock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('driveId', 'd1');
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r)); // queueMicrotask load
    mock.expectOne((r) => r.url === URL).flush(pageOf([applicant('a1'), applicant('a2')]));
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }
  afterEach(() => mock.verify());

  it('loads applicants into the table', async () => {
    await setup();
    expect(fixture.componentInstance.rows().length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Anjali');
  });

  it('toggling a status filter re-queries the server with the status param', async () => {
    await setup();
    fixture.componentInstance.toggleStatus('SHORTLISTED');
    const req = mock.expectOne((r) => r.url === URL);
    expect(req.request.params.getAll('status')).toEqual(['SHORTLISTED']);
    req.flush(pageOf([applicant('a1')]));
  });

  it('selecting rows + Shortlist POSTs the ids then refetches', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.toggleOne('a1');
    cmp.toggleOne('a2');
    expect(cmp.selected().size).toBe(2);
    cmp.shortlist();
    const post = mock.expectOne(URL + '/shortlist');
    expect(post.request.body).toEqual({ applicationIds: ['a1', 'a2'] });
    post.flush({ succeeded: ['a1', 'a2'], failed: [], succeededCount: 2, failedCount: 0 });
    await new Promise((r) => setTimeout(r)); // let the bulk promise resolve then trigger the refetch
    mock.expectOne((r) => r.url === URL).flush(pageOf([]));
    await new Promise((r) => setTimeout(r));
    expect(cmp.selected().size).toBe(0);
  });

  it('Select surfaces the soft openings warning', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.toggleOne('a1');
    cmp.ask('select');
    cmp.runConfirm();
    mock.expectOne(URL + '/select').flush({ succeeded: ['a1'], failed: [], succeededCount: 1, failedCount: 0, selectedTotal: 6, openings: 5, warning: 'You have selected more than the 5 openings.' });
    await new Promise((r) => setTimeout(r));
    mock.expectOne((r) => r.url === URL).flush(pageOf([]));
    await new Promise((r) => setTimeout(r));
    // warning surfaced via toast (no throw); selection cleared
    expect(cmp.selected().size).toBe(0);
  });

  it('clears the selection when the filter changes (no stale ids into a bulk POST)', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.toggleOne('a1');
    expect(cmp.selected().size).toBe(1);
    cmp.toggleStatus('SHORTLISTED'); // query change → reload
    expect(cmp.selected().size).toBe(0);
    mock.expectOne((r) => r.url === URL).flush(pageOf([applicant('a3')]));
  });

  it('résumé opens the fresh presigned URL', async () => {
    await setup();
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    fixture.componentInstance.openResume('a1');
    mock.expectOne(URL + '/a1/resume').flush({ url: 'https://signed', expiresInSeconds: 900 });
    await new Promise((r) => setTimeout(r));
    expect(open).toHaveBeenCalledWith('https://signed', '_blank', 'noopener');
  });
});
