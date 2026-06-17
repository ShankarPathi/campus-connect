import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApplicantQuery,
  ApplicantSummary,
  BulkDecisionResponse,
  DefineRoundsRequest,
  DriveRequest,
  DriveResponse,
  OfferResponse,
  PageResponse,
  RecordResultsRequest,
  ReleaseOfferRequest,
  RescheduleRoundRequest,
  ResumeUrlResponse,
  RoundResultsResponse,
  RoundsResponse,
  SelectionResponse,
} from './recruiter.models';
import { applicantParams } from './recruiter.mappers';

const BASE = `${environment.apiBase}/recruiter`;

/** Drives — list/get/create/update(full-replace)/submit/cancel (Epic 5/6). Responses are pre-unwrapped. */
@Injectable({ providedIn: 'root' })
export class DriveService {
  private readonly http = inject(HttpClient);

  list(): Promise<DriveResponse[]> {
    return firstValueFrom(this.http.get<DriveResponse[]>(`${BASE}/drives`));
  }
  get(id: string): Promise<DriveResponse> {
    return firstValueFrom(this.http.get<DriveResponse>(`${BASE}/drives/${id}`));
  }
  create(body: DriveRequest): Promise<DriveResponse> {
    return firstValueFrom(this.http.post<DriveResponse>(`${BASE}/drives`, body));
  }
  update(id: string, body: DriveRequest): Promise<DriveResponse> {
    return firstValueFrom(this.http.put<DriveResponse>(`${BASE}/drives/${id}`, body));
  }
  submit(id: string): Promise<DriveResponse> {
    return firstValueFrom(this.http.post<DriveResponse>(`${BASE}/drives/${id}/submit`, {}));
  }
  cancel(id: string): Promise<DriveResponse> {
    return firstValueFrom(this.http.post<DriveResponse>(`${BASE}/drives/${id}/cancel`, {}));
  }
}

/** Applicants — server-paged list, résumé URL, bulk shortlist/reject/select (Epic 6). */
@Injectable({ providedIn: 'root' })
export class ApplicantService {
  private readonly http = inject(HttpClient);

  list(driveId: string, query: ApplicantQuery): Promise<PageResponse<ApplicantSummary>> {
    const params = new HttpParams({ fromObject: applicantParams(query) });
    return firstValueFrom(
      this.http.get<PageResponse<ApplicantSummary>>(`${BASE}/drives/${driveId}/applicants`, { params }),
    );
  }
  resumeUrl(driveId: string, applicationId: string): Promise<ResumeUrlResponse> {
    return firstValueFrom(
      this.http.get<ResumeUrlResponse>(`${BASE}/drives/${driveId}/applicants/${applicationId}/resume`),
    );
  }
  shortlist(driveId: string, applicationIds: string[]): Promise<BulkDecisionResponse> {
    return firstValueFrom(
      this.http.post<BulkDecisionResponse>(`${BASE}/drives/${driveId}/applicants/shortlist`, { applicationIds }),
    );
  }
  reject(driveId: string, applicationIds: string[]): Promise<BulkDecisionResponse> {
    return firstValueFrom(
      this.http.post<BulkDecisionResponse>(`${BASE}/drives/${driveId}/applicants/reject`, { applicationIds }),
    );
  }
  select(driveId: string, applicationIds: string[]): Promise<SelectionResponse> {
    return firstValueFrom(
      this.http.post<SelectionResponse>(`${BASE}/drives/${driveId}/applicants/select`, { applicationIds }),
    );
  }
}

/** Interview rounds — define/list/reschedule/record-results (Epic 6). */
@Injectable({ providedIn: 'root' })
export class RoundService {
  private readonly http = inject(HttpClient);

  getRounds(driveId: string): Promise<RoundsResponse> {
    return firstValueFrom(this.http.get<RoundsResponse>(`${BASE}/drives/${driveId}/rounds`));
  }
  defineRounds(driveId: string, body: DefineRoundsRequest): Promise<RoundsResponse> {
    return firstValueFrom(this.http.put<RoundsResponse>(`${BASE}/drives/${driveId}/rounds`, body));
  }
  reschedule(driveId: string, roundOrder: number, body: RescheduleRoundRequest): Promise<RoundsResponse> {
    return firstValueFrom(
      this.http.patch<RoundsResponse>(`${BASE}/drives/${driveId}/rounds/${roundOrder}/reschedule`, body),
    );
  }
  recordResults(driveId: string, roundOrder: number, body: RecordResultsRequest): Promise<RoundResultsResponse> {
    return firstValueFrom(
      this.http.post<RoundResultsResponse>(`${BASE}/drives/${driveId}/rounds/${roundOrder}/results`, body),
    );
  }
}

/** Offer release — multipart PDF + terms (Epic 7). No recruiter offers-list endpoint. */
@Injectable({ providedIn: 'root' })
export class OfferService {
  private readonly http = inject(HttpClient);

  release(driveId: string, applicationId: string, data: ReleaseOfferRequest, file: File): Promise<OfferResponse> {
    const form = new FormData();
    form.append('file', file);
    form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
    return firstValueFrom(
      this.http.post<OfferResponse>(`${BASE}/drives/${driveId}/applicants/${applicationId}/offer`, form),
    );
  }
}
