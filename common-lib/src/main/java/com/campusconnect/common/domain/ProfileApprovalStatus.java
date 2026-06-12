package com.campusconnect.common.domain;

/**
 * Lifecycle of a student's placement profile (Epic 3). Story 3.1 uses {@code DRAFT → PENDING_APPROVAL}
 * on submit; {@code APPROVED}/{@code REJECTED} are written by the College-Admin review path in Story 3.3.
 * This is independent of the {@code isLocked} season-freeze flag (Story 3.4) — approval ≠ lock.
 */
public enum ProfileApprovalStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
