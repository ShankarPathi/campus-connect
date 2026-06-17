/** TS mirrors of the admin-service DTOs (Story 9.6). Field names match the backend JSON exactly. */

// ── Dashboard (Epic 8) ──
export interface DashboardSnapshot {
  pendingProfileApprovals: number;
  pendingRecruiterApprovals: number;
  pendingDriveApprovals: number;
  totalStudents: number;
  totalDrives: number;
  totalApplications: number;
  placedStudents: number;
}

// ── Profiles (Epic 3) ──
export type ProfileApprovalStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
export interface PendingProfile {
  studentId: string;
  rollNumber: string | null;
  fullName: string | null;
  branch: string | null;
  cgpa: number | null;
  activeBacklogs: number | null;
  batch: string | null;
  completionPercent: number;
  isLocked: boolean;
}
export interface AdminEditProfileRequest {
  branch: string | null;
  cgpa: number | null;
  activeBacklogs: number | null;
  batch: string | null;
}

// ── Recruiters (Epic 2) ──
export type AccountStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'PENDING_APPROVAL' | 'REJECTED' | 'DEACTIVATED';
export interface PendingRecruiter {
  userId: string;
  email: string;
  companyName: string | null;
  companyWebsite: string | null;
  industry: string | null;
  companyDescription: string | null;
  recruiterDesignation: string | null;
  contactPhone: string | null;
}

// ── Drives (Epic 4) ──
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
export interface PendingDrive {
  id: string;
  companyName: string;
  role: string | null;
  packageLpa: number | null;
  location: string | null;
  openings: number | null;
  applyDeadline: string | null;
  status: DriveStatus;
  rejectionReason: string | null;
  eligibility: { branches: string[] | null; minCgpa: number | null; backlogPolicy: BacklogPolicy | null; batch: string | null };
}
export interface AdminEditDriveCriteriaRequest {
  branches: string[] | null;
  minCgpa: number | null;
  batch: string | null;
}

// ── Placements (Epic 7) ──
export type PlacementStatus = 'PENDING_CONFIRMATION' | 'OFFICIALLY_PLACED';
export interface PlacementRecord {
  id: string;
  studentId: string;
  applicationId: string;
  company: string | null;
  ctc: number | null;
  role: string | null;
  joiningDate: string | null;
  status: PlacementStatus;
}

// ── Eligibility policy (Epic 5) ──
export interface EligibilityPolicy {
  minCgpaFloor?: number | null;
  placedStudentsMayApply: boolean;
  reapplyPackageThresholdLpa?: number | null;
}
export interface UpdateEligibilityPolicyRequest {
  minCgpaFloor: number | null;
  reapplyPackageThresholdLpa: number | null;
}

// ── Reports (Epic 8) ──
export interface OverallStats {
  totalStudents: number;
  placedStudents: number;
  placementPercent: number;
}
export interface BranchStat {
  branch: string;
  totalStudents: number;
  placedStudents: number;
  placementPercent: number;
}
export interface CompanyStat {
  company: string;
  placements: number;
}
export interface PlacementReport {
  overall: OverallStats;
  branchwise: BranchStat[];
  companywise: CompanyStat[];
}

/** Reject body shared by profile/recruiter/drive rejections. */
export interface RejectRequest {
  reason: string;
}
