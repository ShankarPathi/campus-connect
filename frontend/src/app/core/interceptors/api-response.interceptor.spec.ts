import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { apiResponseInterceptor } from './api-response.interceptor';
import { ApiResponseError } from '../http/api.models';

describe('apiResponseInterceptor', () => {
  let http: HttpClient;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([apiResponseInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    mock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => mock.verify());

  it('unwraps a JSON success envelope to its data', () => {
    let result: unknown;
    http.get('/api/student/notifications/unread-count').subscribe((r) => (result = r));
    mock
      .expectOne('/api/student/notifications/unread-count')
      .flush({ success: true, data: { unreadCount: 3 } }, { headers: { 'content-type': 'application/json' } });
    expect(result).toEqual({ unreadCount: 3 });
  });

  it('passes a text/csv response through untouched (CSV export)', () => {
    let result: unknown;
    http.get('/api/admin/reports/placements/export', { responseType: 'text' }).subscribe((r) => (result = r));
    mock
      .expectOne('/api/admin/reports/placements/export')
      .flush('rollNumber,name\nR1,Asha', { headers: { 'content-type': 'text/csv' } });
    expect(result).toBe('rollNumber,name\nR1,Asha');
  });

  it('maps a success:false error envelope to a typed ApiResponseError', () => {
    let err: unknown;
    http.post('/api/student/applications', {}).subscribe({ error: (e) => (err = e) });
    mock
      .expectOne('/api/student/applications')
      .flush(
        { success: false, error: { code: 'CONFLICT', message: 'Already applied' } },
        { status: 409, statusText: 'Conflict', headers: { 'content-type': 'application/json' } },
      );
    expect(err).toBeInstanceOf(ApiResponseError);
    expect((err as ApiResponseError).code).toBe('CONFLICT');
  });
});
