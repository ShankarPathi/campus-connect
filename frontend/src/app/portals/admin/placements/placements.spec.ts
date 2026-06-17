import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlacementsPage } from './placements';

const PENDING_ROW = {
  id: 'p1',
  studentId: 's1',
  applicationId: 'a1',
  company: 'TCS',
  ctc: 7,
  role: 'SDE',
  joiningDate: null,
  status: 'PENDING_CONFIRMATION',
};

describe('PlacementsPage', () => {
  let mock: HttpTestingController;
  let fixture: ComponentFixture<PlacementsPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    mock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlacementsPage);
  });

  afterEach(() => mock.verify());

  // The constructor kicks off load() synchronously; flush the GET then yield a microtask turn.
  async function settle(rows: unknown[] = [PENDING_ROW]): Promise<void> {
    fixture.detectChanges();
    const req = mock.expectOne((r) => r.url === '/api/admin/placements');
    req.flush(rows);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }

  it('loads and renders a row with its status label and a Confirm button for a pending placement', async () => {
    await settle();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('TCS');
    expect(text).toContain('₹7 LPA');
    expect(text).toContain('Pending confirmation');
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('app-button');
    const labels = Array.from(buttons).map((b) => b.textContent ?? '');
    expect(labels.some((l) => l.includes('Confirm placement'))).toBe(true);
  });

  it('confirms a pending placement: POSTs to confirm then refetches', async () => {
    await settle();
    fixture.componentInstance.openConfirm(PENDING_ROW as never);
    void fixture.componentInstance.doConfirm();
    const post = mock.expectOne('/api/admin/placements/p1/confirm');
    expect(post.request.method).toBe('POST');
    post.flush({ ...PENDING_ROW, status: 'OFFICIALLY_PLACED' });
    await new Promise((r) => setTimeout(r));
    // run() refetches via load()
    mock.expectOne((r) => r.url === '/api/admin/placements').flush([]);
    await new Promise((r) => setTimeout(r));
  });

  it('switches the filter to OFFICIALLY_PLACED and re-queries with that status param', async () => {
    await settle();
    fixture.componentInstance.setStatus('OFFICIALLY_PLACED');
    const req = mock.expectOne((r) => r.url === '/api/admin/placements');
    expect(req.request.params.get('status')).toBe('OFFICIALLY_PLACED');
    req.flush([]);
    await new Promise((r) => setTimeout(r));
  });
});
