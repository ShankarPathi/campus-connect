import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApplicationsPage } from './applications';

const APPLICATIONS_URL = '/api/student/applications';

function appBody(over: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'a1',
    driveId: 'd1',
    companyName: 'TCS',
    role: 'SDE',
    status: 'APPLIED',
    appliedAt: '2026-06-01T00:00:00Z',
    ...over,
  };
}

describe('ApplicationsPage', () => {
  let mock: HttpTestingController;

  function setup(): ComponentFixture<ApplicationsPage> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    mock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ApplicationsPage);
    fixture.detectChanges();
    return fixture;
  }
  afterEach(() => mock.verify());

  // The isolated spec has no interceptors, so the service's HttpClient returns the raw flushed body.
  function flushList(body: unknown[]): void {
    const req = mock.expectOne(APPLICATIONS_URL);
    expect(req.request.method).toBe('GET');
    req.flush(body);
  }

  it('renders a card with the plain status label and a stepper after load', async () => {
    const fixture = setup();
    flushList([appBody()]);
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('TCS');
    expect(el.textContent).toContain('SDE');
    // plain status label, never the raw enum
    expect(el.querySelector('app-status-pill')?.textContent).toContain('Applied');
    expect(el.textContent).not.toContain('APPLIED');
    expect(el.querySelector('app-stepper')).toBeTruthy();
  });

  it('hides Withdraw for a SHORTLISTED app and shows it for an APPLIED app', async () => {
    const fixture = setup();
    flushList([
      appBody({ id: 'a1', status: 'APPLIED' }),
      appBody({ id: 'a2', status: 'SHORTLISTED' }),
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('.card') as NodeListOf<HTMLElement>;
    expect(cards.length).toBe(2);
    expect(cards[0].querySelector('app-button')).toBeTruthy(); // APPLIED → withdrawable
    expect(cards[1].querySelector('app-button')).toBeFalsy(); // SHORTLISTED → not
  });

  it('confirming withdraw POSTs to the withdraw endpoint and updates the card', async () => {
    const fixture = setup();
    flushList([appBody({ id: 'a1', status: 'APPLIED' })]);
    await fixture.whenStable();
    fixture.detectChanges();

    const cmp = fixture.componentInstance;
    cmp.openWithdraw({
      id: 'a1',
      driveId: 'd1',
      companyName: 'TCS',
      role: 'SDE',
      status: 'APPLIED',
      appliedAt: '2026-06-01T00:00:00Z',
    });

    const done = cmp.confirmWithdraw();
    const req = mock.expectOne(`${APPLICATIONS_URL}/a1/withdraw`);
    expect(req.request.method).toBe('POST');
    req.flush(appBody({ id: 'a1', status: 'WITHDRAWN' }));
    await done;
    await fixture.whenStable();
    fixture.detectChanges();

    expect(cmp.applications()[0].status).toBe('WITHDRAWN');
    expect(fixture.nativeElement.querySelector('app-status-pill')?.textContent).toContain('Withdrawn');
  });
});
