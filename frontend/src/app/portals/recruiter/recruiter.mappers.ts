import {
  ApplicantQuery,
  ApplicationStatus,
  DriveResponse,
  DriveStatus,
  InterviewMode,
  RoundResult,
} from './recruiter.models';

/** Plain-language labels (UX-DR3 — never render a raw enum). */
export function driveStatusLabel(s: DriveStatus): string {
  const m: Record<DriveStatus, string> = {
    DRAFT: 'Draft',
    PENDING_APPROVAL: 'Pending approval',
    PUBLISHED: 'Published',
    ONGOING: 'Ongoing',
    CLOSED: 'Closed',
    COMPLETED: 'Completed',
    REJECTED_BY_ADMIN: 'Changes requested',
    CANCELLED: 'Cancelled',
  };
  return m[s];
}

export function applicantStatusLabel(s: ApplicationStatus): string {
  const m: Record<ApplicationStatus, string> = {
    APPLIED: 'Applied',
    UNDER_REVIEW: 'Under review',
    SHORTLISTED: 'Shortlisted',
    INTERVIEWING: 'Interviewing',
    SELECTED: 'Selected',
    OFFER_RELEASED: 'Offer released',
    OFFER_ACCEPTED: 'Offer accepted',
    OFFER_DECLINED: 'Offer declined',
    OFFER_EXPIRED: 'Offer expired',
    REJECTED: 'Rejected',
    WITHDRAWN: 'Withdrawn',
  };
  return m[s];
}

export function modeLabel(m: InterviewMode): string {
  return m === 'ONLINE' ? 'Online' : 'In person';
}
export function resultLabel(r: RoundResult): string {
  const m: Record<RoundResult, string> = { PENDING: 'Pending', PASS: 'Passed', FAIL: 'Failed', ABSENT: 'Absent' };
  return m[r];
}

/** Drive can be edited / resubmitted only while a draft or after changes were requested. */
export function isDriveEditable(s: DriveStatus): boolean {
  return s === 'DRAFT' || s === 'REJECTED_BY_ADMIN';
}

/** The offer-lifecycle statuses shown in the Offers tab. */
export const OFFER_STATUSES: ApplicationStatus[] = ['OFFER_RELEASED', 'OFFER_ACCEPTED', 'OFFER_DECLINED', 'OFFER_EXPIRED'];

export interface DriveCounts {
  drafts: number;
  pending: number;
  open: number;
  total: number;
}
/** Dashboard tiles derived from the my-drives list (no recruiter-dashboard API). */
export function driveCounts(drives: DriveResponse[]): DriveCounts {
  return {
    drafts: drives.filter((d) => d.status === 'DRAFT' || d.status === 'REJECTED_BY_ADMIN').length,
    pending: drives.filter((d) => d.status === 'PENDING_APPROVAL').length,
    open: drives.filter((d) => d.status === 'PUBLISHED' || d.status === 'ONGOING').length,
    total: drives.length,
  };
}

/**
 * Build the applicants query param map for HttpParams — omit empty/undefined so the URL stays clean.
 * `status` is repeated (an array); the rest are scalars.
 */
export function applicantParams(q: ApplicantQuery): Record<string, string | string[]> {
  const params: Record<string, string | string[]> = {};
  if (q.status?.length) {
    params['status'] = q.status;
  }
  if (q.search?.trim()) {
    params['search'] = q.search.trim();
  }
  if (q.sortBy) {
    params['sortBy'] = q.sortBy;
  }
  if (q.sortDir) {
    params['sortDir'] = q.sortDir;
  }
  if (q.page != null) {
    params['page'] = String(q.page);
  }
  if (q.pageSize != null) {
    params['pageSize'] = String(q.pageSize);
  }
  return params;
}
