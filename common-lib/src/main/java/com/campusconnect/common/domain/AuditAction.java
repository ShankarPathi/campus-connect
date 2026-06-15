package com.campusconnect.common.domain;

/**
 * The auditable actions recorded in {@code auditLogs} (Story 3.3). Grows as later epics audit more
 * sensitive admin/recruiter actions (architecture §11). Stored as the enum name in the document.
 */
public enum AuditAction {
    PROFILE_APPROVED,
    PROFILE_REJECTED,
    PROFILE_EDITED,
    PROFILE_LOCKED,
    PROFILE_UNLOCKED,
    DRIVE_APPROVED,
    DRIVE_REJECTED,
    DRIVE_EDITED,
    DRIVE_CANCELLED,
    POLICY_EDITED,
    APPLICANT_SHORTLISTED,
    APPLICANT_REJECTED,
    INTERVIEW_ROUNDS_DEFINED,
    ROUND_RESCHEDULED
}
