import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardPage } from './dashboard';

describe('DashboardPage', () => {
  let fixture: ComponentFixture<DashboardPage>;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(DashboardPage);
    mock = TestBed.inject(HttpTestingController);
  });

  function flushAll() {
    mock.expectOne('/api/student/profile').flush({ completionPercent: 70 });
    mock.expectOne('/api/student/drives').flush([
      { id: 'd1', companyName: 'C', role: 'R', status: 'PUBLISHED', group: 'ELIGIBLE', failedCriteria: null },
      { id: 'd2', companyName: 'C', role: 'R', status: 'PUBLISHED', group: 'NOT_ELIGIBLE', failedCriteria: ['x'] },
    ]);
    mock.expectOne('/api/student/applications').flush([
      { id: 'a1', driveId: 'd1', status: 'APPLIED', appliedAt: 't' },
      { id: 'a2', driveId: 'd2', status: 'WITHDRAWN', appliedAt: 't' },
    ]);
    mock.expectOne('/api/student/offers').flush([{ id: 'o1', applicationId: 'a1', role: 'R', status: 'PENDING' }]);
  }

  it('composes the tiles from the list endpoints', async () => {
    fixture.detectChanges();
    flushAll();
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();

    const cmp = fixture.componentInstance;
    expect(cmp.completion()).toBe(70);
    expect(cmp.eligible()).toBe(1);
    expect(cmp.activeApplications()).toBe(1); // WITHDRAWN excluded
    expect(cmp.offers()).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Complete your profile');
  });

  it('shows the error state when a call fails', async () => {
    fixture.detectChanges();
    mock.expectOne('/api/student/profile').flush(null, { status: 500, statusText: 'err' });
    mock.expectOne('/api/student/drives').flush([]);
    mock.expectOne('/api/student/applications').flush([]);
    mock.expectOne('/api/student/offers').flush([]);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('error');
  });
});
