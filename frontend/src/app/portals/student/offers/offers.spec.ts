import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OffersPage } from './offers';

const PENDING = {
  id: 'o1',
  applicationId: 'ap1',
  role: 'SDE',
  ctc: 12,
  joiningDate: null,
  acceptanceDeadline: null,
  status: 'PENDING',
};
const ACCEPTED = { ...PENDING, id: 'o2', role: 'Analyst', status: 'ACCEPTED' };

describe('OffersPage', () => {
  let mock: HttpTestingController;

  async function setup(list: unknown[]): Promise<ComponentFixture<OffersPage>> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    mock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(OffersPage);
    fixture.detectChanges();
    mock.expectOne('/api/student/offers').flush(list);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => mock.verify());

  it('renders offer cards with the plain status label after load', async () => {
    const fixture = await setup([PENDING, ACCEPTED]);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('SDE');
    expect(text).toContain('₹12 LPA');
    // Plain label, never the raw enum.
    expect(text).toContain('Awaiting your response');
    expect(text).not.toContain('PENDING');
  });

  it('opening detail GETs the offer and shows the letter link when offerLetterUrl is present', async () => {
    const fixture = await setup([PENDING]);
    fixture.componentInstance.openDetail('o1');
    const req = mock.expectOne('/api/student/offers/o1');
    expect(req.request.method).toBe('GET');
    req.flush({ ...PENDING, studentId: 's1', offerLetterUrl: 'https://files.example/o1.pdf?sig=abc' });
    await fixture.whenStable();
    fixture.detectChanges();

    const link = (fixture.nativeElement as HTMLElement).querySelector(
      'a[target="_blank"]',
    ) as HTMLAnchorElement | null;
    expect(link).not.toBeNull();
    expect(link?.getAttribute('href')).toBe('https://files.example/o1.pdf?sig=abc');
    expect(link?.textContent).toContain('offer letter');
  });

  it('accepting a PENDING offer POSTs accept and updates the card to ACCEPTED', async () => {
    const fixture = await setup([PENDING]);
    const cmp = fixture.componentInstance;

    cmp.openDetail('o1');
    mock.expectOne('/api/student/offers/o1').flush({ ...PENDING, studentId: 's1', offerLetterUrl: null });
    await fixture.whenStable();

    cmp.askConfirm('accept');
    cmp.accept();
    const req = mock.expectOne('/api/student/offers/o1/accept');
    expect(req.request.method).toBe('POST');
    req.flush({ ...PENDING, studentId: 's1', offerLetterUrl: null, status: 'ACCEPTED' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(cmp.offers()[0].status).toBe('ACCEPTED');
    expect(cmp.detailOpen()).toBe(false);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Accepted');
  });

  it('does not show Accept/Decline for a non-PENDING offer', async () => {
    const fixture = await setup([ACCEPTED]);
    const cmp = fixture.componentInstance;

    cmp.openDetail('o2');
    mock.expectOne('/api/student/offers/o2').flush({ ...ACCEPTED, studentId: 's1', offerLetterUrl: null });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Confirm accept');
    // No accept/decline action buttons rendered in the modal footer for a settled offer.
    const buttons = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.panel__foot button'),
    ).map((b) => b.textContent?.trim());
    expect(buttons).not.toContain('Accept');
    expect(buttons).not.toContain('Decline');
  });
});
