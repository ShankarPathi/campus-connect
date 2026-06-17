import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { DriveWorkspacePage } from './workspace';
import { DriveResponse } from '../recruiter.models';

const eligibility = { branches: null, minCgpa: null, backlogPolicy: null, batch: null };
const drive = (over: Partial<DriveResponse> = {}): DriveResponse => ({
  id: 'd1', companyName: 'Acme', role: 'SDE', packageLpa: 12, location: 'Pune', eligibility,
  openings: 5, applyDeadline: null, status: 'DRAFT', rejectionReason: null, ...over,
});

describe('DriveWorkspacePage', () => {
  let fixture: ComponentFixture<DriveWorkspacePage>;
  let mock: HttpTestingController;
  const URL = '/api/recruiter/drives/d1';

  function create() {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'd1' } } } },
      ],
    });
    fixture = TestBed.createComponent(DriveWorkspacePage);
    mock = TestBed.inject(HttpTestingController);
  }
  afterEach(() => mock.verify());

  it('loads the drive and renders the overview header', async () => {
    create();
    mock.expectOne((r) => r.method === 'GET' && r.url === URL).flush(drive());
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    const cmp = fixture.componentInstance;
    expect(cmp.state()).toBe('ready');
    expect(cmp.editable()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Acme · SDE');
  });

  it('shows notfound on a 404', async () => {
    create();
    mock.expectOne((r) => r.url === URL).flush(null, { status: 404, statusText: 'Not Found' });
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('notfound');
  });
});
