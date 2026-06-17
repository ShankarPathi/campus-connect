import { EligibilityCheck, SegmentItem, StepItem } from '../../shared/ui';
import { ApplicationStatus, EligibilityGroup, OfferStatus, ProfileApprovalStatus, StudentDrive } from './student.models';

/** Plain-language labels (UX-DR3 — never render a raw enum). */
export function applicationStatusLabel(s: ApplicationStatus): string {
  const m: Record<ApplicationStatus, string> = {
    APPLIED: 'Applied',
    UNDER_REVIEW: 'Under review',
    SHORTLISTED: 'Shortlisted',
    INTERVIEWING: 'Interviewing',
    SELECTED: 'Selected',
    OFFER_RELEASED: 'Offer received',
    OFFER_ACCEPTED: 'Offer accepted',
    OFFER_DECLINED: 'Offer declined',
    OFFER_EXPIRED: 'Offer expired',
    REJECTED: 'Not selected',
    WITHDRAWN: 'Withdrawn',
  };
  return m[s];
}

export function offerStatusLabel(s: OfferStatus): string {
  const m: Record<OfferStatus, string> = {
    PENDING: 'Awaiting your response',
    ACCEPTED: 'Accepted',
    DECLINED: 'Declined',
    EXPIRED: 'Expired',
  };
  return m[s];
}

export function profileStatusLabel(s: ProfileApprovalStatus): string {
  const m: Record<ProfileApprovalStatus, string> = {
    DRAFT: 'Draft',
    PENDING_APPROVAL: 'Pending approval',
    APPROVED: 'Approved',
    REJECTED: 'Changes requested',
  };
  return m[s];
}

const GROUP_LABEL: Record<EligibilityGroup, string> = {
  ELIGIBLE: 'Eligible',
  APPLIED: 'Applied',
  NOT_ELIGIBLE: 'Not eligible',
  CLOSED: 'Closed',
};
export const GROUP_ORDER: EligibilityGroup[] = ['ELIGIBLE', 'APPLIED', 'NOT_ELIGIBLE', 'CLOSED'];

/** The four `segmented_sections` with live counts, always in IA order. */
export function driveSections(drives: StudentDrive[]): SegmentItem[] {
  return GROUP_ORDER.map((g) => ({
    key: g,
    label: GROUP_LABEL[g],
    count: drives.filter((d) => d.group === g).length,
  }));
}

/**
 * The eligibility panel rows for a drive. The student API returns only the *failed* criteria, so an
 * ELIGIBLE drive shows a single positive row and a NOT_ELIGIBLE drive shows one danger row per reason.
 * The first failing rule is surfaced first (FR-12/13).
 */
export function eligibilityChecks(drive: StudentDrive): EligibilityCheck[] {
  if (drive.group === 'NOT_ELIGIBLE' && drive.failedCriteria?.length) {
    return drive.failedCriteria.map((reason) => ({ label: reason, passed: false, detail: reason }));
  }
  if (drive.group === 'ELIGIBLE') {
    return [{ label: 'Eligible', passed: true, detail: 'You meet all the criteria for this drive.' }];
  }
  return [];
}

/** The first blocking reason shown compactly on a NOT_ELIGIBLE card. */
export function firstFailedReason(drive: StudentDrive): string | null {
  return drive.group === 'NOT_ELIGIBLE' ? (drive.failedCriteria?.[0] ?? null) : null;
}

const STEP_LABELS = ['Applied', 'Under review', 'Shortlisted', 'Interviewing', 'Selected', 'Offer'] as const;
/** How far each status has progressed along the canonical track (index into STEP_LABELS). */
const STATUS_REACH: Record<ApplicationStatus, number> = {
  APPLIED: 0,
  UNDER_REVIEW: 1,
  SHORTLISTED: 2,
  INTERVIEWING: 3,
  SELECTED: 4,
  OFFER_RELEASED: 5,
  OFFER_ACCEPTED: 5,
  OFFER_DECLINED: 5,
  OFFER_EXPIRED: 5,
  REJECTED: -1,
  WITHDRAWN: -1,
};

/** Statuses that have *settled* at their reached node — the node is `done`, never a fake "current". */
const SETTLED_AT_REACH: ApplicationStatus[] = ['OFFER_ACCEPTED', 'OFFER_DECLINED', 'OFFER_EXPIRED'];

/**
 * Build the interview stepper from the application lifecycle (no per-round data on the student side).
 * Terminal REJECTED/WITHDRAWN stop the track (all upcoming — the status pill carries the outcome); a
 * settled offer (accepted/declined/expired) marks its reached node `done`. No status shows a fake
 * "current" node on a closed lifecycle.
 */
export function applicationSteps(status: ApplicationStatus): StepItem[] {
  const stopped = status === 'REJECTED' || status === 'WITHDRAWN';
  const settled = SETTLED_AT_REACH.includes(status);
  const reach = STATUS_REACH[status];
  return STEP_LABELS.map((label, i) => {
    let state: StepItem['state'];
    if (stopped) {
      state = 'upcoming';
    } else if (i < reach) {
      state = 'done';
    } else if (i === reach) {
      state = settled ? 'done' : 'current';
    } else {
      state = 'upcoming';
    }
    return { label, state };
  });
}

/** Withdraw is allowed only pre-shortlist (FR-16). */
export function canWithdraw(status: ApplicationStatus): boolean {
  return status === 'APPLIED' || status === 'UNDER_REVIEW';
}
