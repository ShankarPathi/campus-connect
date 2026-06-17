import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RecruiterDashboardPage } from './dashboard';

const eligibility = { branches: null, minCgpa: null, backlogPolicy: null, batch: null };
const drive = (id: string, status: string) => ({
  id, companyName: 'C', role: 'R', packageLpa: null, location: null, eligibility,
  openings: null, applyDeadline: null, status, rejectionReason: null,
});

describe('RecruiterDashboardPage', () => {
  let fixture: ComponentFixture<RecruiterDashboardPage>;
  let mock: HttpTestingController;
  const URL = '/api/recruiter/drives';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(RecruiterDashboardPage);
    mock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => mock.verify());

  it('derives the status tiles from the my-drives list', async () => {
    mock.expectOne((r) => r.url === URL).flush([
      drive('d1', 'DRAFT'),
      drive('d2', 'PENDING_APPROVAL'),
      drive('d3', 'PUBLISHED'),
    ]);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    expect(fixture.componentInstance.counts()).toEqual({ drafts: 1, pending: 1, open: 1, total: 3 });
    expect(fixture.componentInstance.state()).toBe('ready');
  });

  it('shows the error state when the list fails', async () => {
    mock.expectOne((r) => r.url === URL).flush(null, { status: 500, statusText: 'err' });
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('error');
  });
});
