import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RecruiterApprovalsPage } from './recruiters';

const REC = { userId: 'u1', email: 'r@acme.com', companyName: 'Acme', companyWebsite: null, industry: 'Tech', companyDescription: null, recruiterDesignation: 'HR', contactPhone: null };

describe('RecruiterApprovalsPage', () => {
  let fixture: ComponentFixture<RecruiterApprovalsPage>;
  let mock: HttpTestingController;
  const URL = '/api/admin/recruiters';

  async function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(RecruiterApprovalsPage);
    mock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    mock.expectOne((r) => r.url === URL).flush([REC]);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }
  afterEach(() => mock.verify());

  it('loads pending recruiters with company details', async () => {
    await setup();
    expect(fixture.componentInstance.rows().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Acme');
    expect(fixture.nativeElement.textContent).toContain('Tech');
  });

  it('approve POSTs then refetches', async () => {
    await setup();
    fixture.componentInstance.approve(REC);
    mock.expectOne(URL + '/u1/approve').flush(true);
    await new Promise((r) => setTimeout(r));
    mock.expectOne((r) => r.url === URL).flush([]);
  });
});
