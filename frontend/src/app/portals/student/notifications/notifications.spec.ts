import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationsPage } from './notifications';

const listBody = (isRead = false) => ({
  items: [
    { id: 'n1', type: 'X', title: 'Drive published', message: 'TCS', isRead, createdAt: '2026-06-01T00:00:00Z' },
  ],
  total: 1,
  unreadCount: isRead ? 0 : 1,
  page: 0,
  size: 20,
});

describe('NotificationsPage', () => {
  let fixture: ComponentFixture<NotificationsPage>;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(NotificationsPage);
    mock = TestBed.inject(HttpTestingController);
  });

  const flushList = (isRead = false) =>
    mock.expectOne((r) => r.url === '/api/student/notifications').flush(listBody(isRead));

  it('renders a row after load with an unread marker', async () => {
    fixture.detectChanges();
    flushList(false);
    await fixture.whenStable();
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Drive published');
    expect(html).toContain('TCS');
    const row = (fixture.nativeElement as HTMLElement).querySelector('.row--unread');
    expect(row).not.toBeNull();
    expect(row?.textContent).toContain('Unread');
  });

  it('Mark read POSTs to the read endpoint and clears the row unread state', async () => {
    fixture.detectChanges();
    flushList(false);
    await fixture.whenStable();
    fixture.detectChanges();

    const btn = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find(
      (b) => b.textContent?.includes('Mark read'),
    ) as HTMLButtonElement;
    btn.click();

    const req = mock.expectOne('/api/student/notifications/n1/read');
    expect(req.request.method).toBe('POST');
    req.flush({ unreadCount: 0 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.row--unread')).toBeNull();
  });

  it('Mark all read POSTs to the read-all endpoint', async () => {
    fixture.detectChanges();
    flushList(false);
    await fixture.whenStable();
    fixture.detectChanges();

    const btn = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find(
      (b) => b.textContent?.includes('Mark all read'),
    ) as HTMLButtonElement;
    btn.click();

    const req = mock.expectOne('/api/student/notifications/read-all');
    expect(req.request.method).toBe('POST');
    req.flush({ unreadCount: 0 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.row--unread')).toBeNull();
  });
});
