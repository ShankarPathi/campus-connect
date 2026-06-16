package com.campusconnect.common.domain;

/**
 * The kind of in-app notification (architecture §9, Story 8.1, FR-28) — the frontend switches on this for
 * icon/route. Stored as the enum name. Grows as more lifecycle events are wired. {@code DRIVE_PUBLISHED} and
 * {@code ROUND_RESCHEDULED} are reserved for the deferred audience-resolved fan-outs (drive-published →
 * eligible students; round-reschedule → the round roster).
 */
public enum NotificationType {
    PROFILE_APPROVED,
    PROFILE_REJECTED,
    APPLICATION_SHORTLISTED,
    APPLICATION_REJECTED,
    ROUND_RESULT,
    APPLICANT_SELECTED,
    OFFER_RELEASED,
    OFFER_EXPIRED,
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    PLACEMENT_CONFIRMED,
    DRIVE_PUBLISHED,
    ROUND_RESCHEDULED
}
