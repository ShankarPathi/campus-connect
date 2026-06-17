/** The backend response envelope + a typed client error (Story 9.2). Mirrors common-lib ApiResponse/ApiError. */

export interface ApiError {
  code: string;
  message: string;
  fields?: Record<string, string>;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  error?: ApiError;
}

/** A typed error thrown when the API returns `success:false` (carries the backend code/message/fields). */
export class ApiResponseError extends Error {
  readonly code: string;
  readonly fields?: Record<string, string>;
  constructor(error: ApiError | null | undefined) {
    super(error?.message ?? 'Request failed');
    this.name = 'ApiResponseError';
    this.code = error?.code ?? 'UNKNOWN';
    this.fields = error?.fields;
  }
}
