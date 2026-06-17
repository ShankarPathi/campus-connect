import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminDashboardPage } from './dashboard';

const SNAP = {
  pendingProfileApprovals: 4, pendingRecruiterApprovals: 2, pendingDriveApprovals: 1,
  totalStudents: 120, totalDrives: 8, totalApplications: 300, placedStudents: 45,
};

describe('AdminDashboardPage', () => {
  let fixture: ComponentFixture<AdminDashboardPage>;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(AdminDashboardPage);
    mock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => mock.verify());

  it('loads the snapshot and renders pending-action tiles that link to the queues', async () => {
    fixture.detectChanges();
    mock.expectOne('/api/admin/dashboard').flush(SNAP);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();

    expect(fixture.componentInstance.snapshot()?.pendingProfileApprovals).toBe(4);
    const links = Array.from(fixture.nativeElement.querySelectorAll('a.tile--action')).map((a) => (a as HTMLAnchorElement).getAttribute('href'));
    expect(links.some((h) => h?.includes('/admin/students'))).toBe(true);
    expect(links.some((h) => h?.includes('/admin/recruiters'))).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('45');
  });

  it('shows the error state when the snapshot fails', async () => {
    fixture.detectChanges();
    mock.expectOne('/api/admin/dashboard').flush(null, { status: 500, statusText: 'err' });
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('error');
  });
});
