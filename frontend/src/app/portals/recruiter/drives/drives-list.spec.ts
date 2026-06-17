import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DrivesListPage } from './drives-list';
import { DriveResponse } from '../recruiter.models';

const eligibility = { branches: null, minCgpa: null, backlogPolicy: null, batch: null };
const drive = (over: Partial<DriveResponse> = {}): DriveResponse => ({
  id: 'd1', companyName: 'Acme', role: 'SDE', packageLpa: 12, location: 'Pune', eligibility,
  openings: 5, applyDeadline: null, status: 'DRAFT', rejectionReason: null, ...over,
});

describe('DrivesListPage', () => {
  let fixture: ComponentFixture<DrivesListPage>;
  let mock: HttpTestingController;
  const URL = '/api/recruiter/drives';

  async function setup(list: unknown[]) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(DrivesListPage);
    mock = TestBed.inject(HttpTestingController);
    mock.expectOne((r) => r.method === 'GET' && r.url === URL).flush(list);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }
  afterEach(() => mock.verify());

  it('renders the company/role and a status pill', async () => {
    await setup([drive()]);
    expect(fixture.componentInstance.drives().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Acme');
    expect(fixture.nativeElement.textContent).toContain('SDE');
    expect(fixture.nativeElement.textContent).toContain('Draft');
  });

  it('submit() POSTs /submit and updates the row to the returned status', async () => {
    await setup([drive()]);
    const cmp = fixture.componentInstance;
    cmp.submit(drive());
    const post = mock.expectOne((r) => r.method === 'POST' && r.url === URL + '/d1/submit');
    post.flush(drive({ status: 'PENDING_APPROVAL' }));
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    expect(cmp.drives()[0].status).toBe('PENDING_APPROVAL');
  });
});
