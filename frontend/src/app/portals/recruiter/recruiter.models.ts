/** TS mirrors of the recruiter-service DTOs (Story 9.5). Field names match the backend JSON exactly. */

// ── Drives (Epic 5/6) ──
export type DriveStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'PUBLISHED'
  | 'ONGOING'
  | 'CLOSED'
  | 'COMPLETED'
  | 'REJECTED_BY_ADMIN'
  | 'CANCELLED';
export type BacklogPolicy = 'NO_BACKLOG' | 'ALLOW_BACKLOG';

export interface EligibilityCriteria {
  branches: string[] | null;
  minCgpa: number | null;
  backlogPolicy: BacklogPolicy | null;
  batch: string | null;
}
export interface DriveRequest {
  role: string | null;
  packageLpa: number | null;
  location: string | null;
  eligibility: EligibilityCriteria;
  openings: number | null;
  applyDeadline: string | null;
}
export interface DriveResponse {
  id: string;
  companyName: string;
  role: string | null;
  packageLpa: number | null;
  location: string | null;
  eligibility: EligibilityCriteria;
  openings: number | null;
  applyDeadline: string | null;
  status: DriveStatus;
  rejectionReason: string | null;
}

// ── Applicants (Epic 6) ──
export type ApplicationStatus =
  | 'APPLIED'
  | 'UNDER_REVIEW'
  | 'SHORTLISTED'
  | 'INTERVIEWING'
  | 'SELECTED'
  | 'OFFER_RELEASED'
  | 'OFFER_ACCEPTED'
  | 'OFFER_DECLINED'
  | 'OFFER_EXPIRED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface ApplicantSummary {
  applicationId: string;
  status: ApplicationStatus;
  appliedAt: string;
  fullName: string | null;
  phone: string | null;
  rollNumber: string | null;
  batch: string | null;
  branch: string | null;
  cgpa: number | null;
  activeBacklogs: number | null;
  skills: string[] | null;
  expectedRole: string | null;
  about: string | null;
  isPlaced: boolean | null;
}

export interface PageResponse<T> {
  items: T[];
  totalCount: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface ApplicantQuery {
  status?: ApplicationStatus[];
  search?: string;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  page?: number;
  pageSize?: number;
}

export interface BulkDecisionRequest {
  applicationIds: string[];
}
export interface FailedItem {
  applicationId: string;
  reason: string;
}
export interface BulkDecisionResponse {
  succeeded: string[];
  failed: FailedItem[];
  succeededCount: number;
  failedCount: number;
}
export interface SelectionResponse extends BulkDecisionResponse {
  selectedTotal: number;
  openings: number | null;
  warning: string | null;
}
export interface ResumeUrlResponse {
  url: string;
  expiresInSeconds: number;
}

// ── Rounds (Epic 6) ──
export type InterviewMode = 'ONLINE' | 'OFFLINE';
export type RoundResult = 'PENDING' | 'PASS' | 'FAIL' | 'ABSENT';

export interface RoundSpec {
  name: string;
  mode: InterviewMode;
  schedule: string;
  venueOrLink: string;
}
export interface DefineRoundsRequest {
  rounds: RoundSpec[];
}
export interface RoundView {
  roundOrder: number;
  name: string;
  mode: InterviewMode;
  schedule: string;
  venueOrLink: string;
  assignedCount: number;
}
export interface RoundsResponse {
  rounds: RoundView[];
}
export interface RescheduleRoundRequest {
  schedule: string;
  venueOrLink: string;
}
export interface ResultEntry {
  applicationId: string;
  result: RoundResult;
}
export interface RecordResultsRequest {
  results: ResultEntry[];
}
export interface RoundResultsResponse {
  succeeded: string[];
  failed: FailedItem[];
  succeededCount: number;
  failedCount: number;
}

// ── Offers (Epic 7) ──
export type OfferStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';
export interface ReleaseOfferRequest {
  role: string;
  ctc: number;
  joiningDate: string;
  acceptanceDeadline: string;
}
export interface OfferResponse {
  id: string;
  applicationId: string;
  studentId: string;
  role: string;
  ctc: number;
  joiningDate: string;
  acceptanceDeadline: string;
  status: OfferStatus;
  offerLetterUrl: string | null;
}
