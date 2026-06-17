import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { catchError, map, throwError } from 'rxjs';
import { ApiResponse, ApiResponseError } from '../http/api.models';

/**
 * Unwraps the standard `{ success, data, message, error }` envelope (Story 9.2): a JSON success body yields
 * `data`; an error envelope becomes a typed {@link ApiResponseError}. **Non-JSON responses pass through
 * untouched** — the Story-8.5 CSV export (`text/csv`/blob) is never unwrapped. A 401 is rethrown as-is so the
 * (outer) auth interceptor can run its refresh-retry.
 */
export const apiResponseInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api')) {
    return next(req);
  }
  return next(req).pipe(
    map((event) => {
      if (event instanceof HttpResponse) {
        const ct = event.headers.get('content-type') ?? '';
        const body = event.body as ApiResponse<unknown> | null;
        if (ct.includes('application/json') && body && typeof body === 'object' && 'success' in body) {
          if (body.success) {
            return event.clone({ body: body.data ?? null });
          }
          throw new ApiResponseError(body.error);
        }
      }
      return event; // text/csv, blobs, non-envelope JSON — untouched
    }),
    catchError((err: unknown) => {
      // let the auth interceptor handle 401; map other error envelopes to a typed error
      if (err instanceof HttpErrorResponse && err.status !== 401) {
        const envelope = err.error as ApiResponse<unknown> | null;
        if (envelope && typeof envelope === 'object' && envelope.success === false) {
          return throwError(() => new ApiResponseError(envelope.error));
        }
      }
      return throwError(() => err);
    }),
  );
};
