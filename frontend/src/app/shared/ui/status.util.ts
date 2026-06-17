import { StatusVariant } from './ui.models';

/**
 * Maps a backend status/enum string to a design-token status variant (Story 9.1) so screens never
 * hard-code colors — they pass a clean `variant` + a human `label` to the status pill. Case/spacing
 * insensitive; unknown statuses fall back to `neutral`.
 */
export function statusToVariant(status: string | null | undefined): StatusVariant {
  if (!status) {
    return 'neutral';
  }
  const key = status.trim().toUpperCase().replace(/[\s-]+/g, '_');

  switch (key) {
    // success — eligible / passed / placed / selected / confirmed
    case 'ELIGIBLE':
    case 'PASS':
    case 'PASSED':
    case 'PLACED':
    case 'SELECTED':
    case 'APPROVED':
    case 'OFFER_ACCEPTED':
    case 'ACCEPTED':
    case 'OFFICIALLY_PLACED':
    case 'PLACEMENT_CONFIRMED':
    case 'ACTIVE':
      return 'success';

    // warning — pending / under review / closing soon (amber = caution, reserved for these only)
    case 'PENDING':
    case 'PENDING_APPROVAL':
    case 'PENDING_CONFIRMATION':
    case 'PENDING_VERIFICATION':
    case 'UNDER_REVIEW':
    case 'CLOSING_SOON':
      return 'warning';

    // danger — not eligible / rejected / failed / expired / declined
    case 'NOT_ELIGIBLE':
    case 'INELIGIBLE':
    case 'REJECTED':
    case 'REJECTED_BY_ADMIN':
    case 'FAIL':
    case 'FAILED':
    case 'ABSENT':
    case 'EXPIRED':
    case 'OFFER_EXPIRED':
    case 'OFFER_DECLINED':
    case 'DECLINED':
    case 'WITHDRAWN':
    case 'CANCELLED':
    case 'DEACTIVATED':
      return 'danger';

    // info — applied / shortlisted / in-progress milestones (positive/actionable, not caution)
    case 'APPLIED':
    case 'SHORTLISTED':
    case 'INTERVIEWING':
    case 'OFFER_RELEASED': // an offer reaching the student is a positive milestone, not amber caution
    case 'PUBLISHED':
    case 'ONGOING':
    case 'INFO':
      return 'info';

    // neutral — by decision (not a fall-through): lifecycle-end / draft drive states
    case 'CLOSED':
    case 'COMPLETED':
    case 'DRAFT':
      return 'neutral';

    default:
      return 'neutral';
  }
}
