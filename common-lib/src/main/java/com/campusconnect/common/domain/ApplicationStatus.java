package com.campusconnect.common.domain;

/**
 * The lifecycle of a student's {@link Application} (architecture §8). Declared whole now — one enum
 * shared across Epics 5–7 — though Story 5.3 only reads it (for the "Applied" grouping) and Story 5.4
 * creates an application in {@code APPLIED}. Later transitions (review, interview rounds, offers) are
 * owned by Epics 6–7. {@code WITHDRAWN} is reachable only pre-shortlist (FR-16); {@code REJECTED} is
 * terminal from any active state. Stored as the enum name in the document.
 */
public enum ApplicationStatus {
    APPLIED,
    UNDER_REVIEW,
    SHORTLISTED,
    INTERVIEWING,
    SELECTED,
    OFFER_RELEASED,
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    OFFER_EXPIRED,
    REJECTED,
    WITHDRAWN
}
