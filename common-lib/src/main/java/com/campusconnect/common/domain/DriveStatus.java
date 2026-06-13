package com.campusconnect.common.domain;

/**
 * The lifecycle of a placement {@link Drive} (architecture §8). Story 4.1 only creates/edits in
 * {@code DRAFT}; the transitions are owned by later Epic-4 stories — submit ({@code DRAFT →
 * PENDING_APPROVAL}, 4.2), admin approve/reject ({@code → PUBLISHED} / {@code → REJECTED_BY_ADMIN},
 * 4.3), and the lifecycle/cancellation moves (4.4 + the Epic-10 jobs: {@code PUBLISHED → ONGOING →
 * CLOSED → COMPLETED}, and {@code CANCELLED}). The full set is declared now so every story shares one
 * enum. Stored as the enum name in the document.
 */
public enum DriveStatus {
    DRAFT,
    PENDING_APPROVAL,
    PUBLISHED,
    ONGOING,
    CLOSED,
    COMPLETED,
    REJECTED_BY_ADMIN,
    CANCELLED
}
