/** TS mirrors of the student-service DTOs (Story 9.4). Field names match the backend JSON exactly. */

// ── Profile (Epic 3) ──
export type ProfileApprovalStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

export interface ProfilePersonal {
  fullName: string | null;
  phone: string | null;
  gender: string | null;
  dateOfBirth: string | null;
  address: string | null;
}
export interface ProfileAcademic {
  branch: string | null;
  cgpa: number | null;
  activeBacklogs: number | null;
}
export interface ProfilePlacement {
  skills: string[] | null;
  expectedRole: string | null;
  about: string | null;
}
export interface StudentProfile {
  studentId: string;
  rollNumber: string | null;
  batch: string | null;
  personal: ProfilePersonal;
  academic: ProfileAcademic;
  placement: ProfilePlacement;
  profileApprovalStatus: ProfileApprovalStatus;
  rejectionReason: string | null;
  isPlaced: boolean;
  isLocked: boolean;
  completionPercent: number;
}
export interface StudentProfileRequest {
  personal: ProfilePersonal;
  academic: ProfileAcademic;
  placement: ProfilePlacement;
  rollNumber: string | null;
  batch: string | null;
}

// ── Resume (Epic 3) ──
export interface ResumeView {
  hasResume: boolean;
  originalName: string | null;
  mimeType: string | null;
  version: number | null;
  sizeBytes: number | null;
  uploadedAt: string | null;
  previewUrl: string | null;
  previewExpiresInSeconds: number | null;
}

// ── Drives (Epic 5) ──
export type DriveStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'PUBLISHED'
  | 'ONGOING'
  | 'CLOSED'
  | 'COMPLETED'
  | 'REJECTED_BY_ADMIN'
  | 'CANCELLED';
export type EligibilityGroup = 'ELIGIBLE' | 'APPLIED' | 'NOT_ELIGIBLE' | 'CLOSED';
export interface StudentDrive {
  id: string;
  companyName: string;
  role: string;
  packageLpa: number | null;
  location: string | null;
  applyDeadline: string | null;
  status: DriveStatus;
  group: EligibilityGroup;
  failedCriteria: string[] | null;
}

// ── Applications (Epic 5) ──
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
export interface StudentApplication {
  id: string;
  driveId: string;
  companyName: string | null;
  role: string | null;
  status: ApplicationStatus;
  appliedAt: string;
}

// ── Offers (Epic 7) ──
export type OfferStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';
export interface OfferSummary {
  id: string;
  applicationId: string;
  role: string;
  ctc: number | null;
  joiningDate: string | null;
  acceptanceDeadline: string | null;
  status: OfferStatus;
}
export interface OfferDetail extends OfferSummary {
  studentId: string;
  offerLetterUrl: string | null;
}

// ── Notifications (Story 8.3) ──
export interface StudentNotification {
  id: string;
  type: string;
  title: string;
  message: string | null;
  isRead: boolean;
  createdAt: string;
}
export interface NotificationList {
  items: StudentNotification[];
  total: number;
  unreadCount: number;
  page: number;
  size: number;
}
export interface UnreadCount {
  unreadCount: number;
}
