import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DriveForm } from './drive-form';
import { DriveResponse } from '../recruiter.models';

const DRIVE: DriveResponse = {
  id: 'd1', companyName: 'Acme', role: 'SDE', packageLpa: 12, location: 'Pune',
  eligibility: { branches: ['CSE', 'ECE'], minCgpa: 7, backlogPolicy: 'NO_BACKLOG', batch: '2026' },
  openings: 5, applyDeadline: null, status: 'DRAFT', rejectionReason: null,
};

describe('DriveForm', () => {
  let fixture: ComponentFixture<DriveForm>;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(DriveForm);
    mock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => mock.verify());

  it('edit submits a FULL DriveRequest (PUT) seeded from the drive', async () => {
    fixture.componentRef.setInput('drive', DRIVE);
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r)); // queueMicrotask seed
    const cmp = fixture.componentInstance;
    expect(cmp.form.get('eligibility.branches')?.value).toBe('CSE, ECE');

    cmp.save();
    const req = mock.expectOne('/api/recruiter/drives/d1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      role: 'SDE', packageLpa: 12, location: 'Pune', openings: 5, applyDeadline: null,
      eligibility: { branches: ['CSE', 'ECE'], minCgpa: 7, backlogPolicy: 'NO_BACKLOG', batch: '2026' },
    });
    req.flush({ ...DRIVE });
  });

  it('create POSTs a new draft', async () => {
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r));
    const cmp = fixture.componentInstance;
    cmp.form.patchValue({ role: 'Analyst' });
    cmp.save();
    const req = mock.expectOne('/api/recruiter/drives');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.role).toBe('Analyst');
    req.flush({ ...DRIVE, id: 'd2', role: 'Analyst' });
  });

  it('renders read-only for a non-editable drive', async () => {
    fixture.componentRef.setInput('drive', { ...DRIVE, status: 'PUBLISHED' });
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r));
    expect(fixture.componentInstance.form.disabled).toBe(true);
    expect(fixture.componentInstance.readOnly()).toBe(true);
  });
});
