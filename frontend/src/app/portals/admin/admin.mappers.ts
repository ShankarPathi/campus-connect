import { AccountStatus, DriveStatus, PlacementStatus, ProfileApprovalStatus } from './admin.models';

/** Plain-language labels (UX-DR3 — never render a raw enum). */
export function profileStatusLabel(s: ProfileApprovalStatus): string {
  const m: Record<ProfileApprovalStatus, string> = {
    DRAFT: 'Draft',
    PENDING_APPROVAL: 'Pending approval',
    APPROVED: 'Approved',
    REJECTED: 'Changes requested',
  };
  return m[s];
}

export function accountStatusLabel(s: AccountStatus): string {
  const m: Record<AccountStatus, string> = {
    PENDING_VERIFICATION: 'Awaiting verification',
    ACTIVE: 'Active',
    PENDING_APPROVAL: 'Pending approval',
    REJECTED: 'Rejected',
    DEACTIVATED: 'Deactivated',
  };
  return m[s];
}

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

export function placementStatusLabel(s: PlacementStatus): string {
  return s === 'PENDING_CONFIRMATION' ? 'Pending confirmation' : 'Officially placed';
}
