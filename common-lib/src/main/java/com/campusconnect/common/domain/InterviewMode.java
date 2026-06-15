package com.campusconnect.common.domain;

/**
 * How an interview round is conducted (Story 6.3, FR-20). Minimal two-value set sufficient for the
 * recruiter's "mode + venue/link" — {@code ONLINE} carries a meeting link, {@code OFFLINE} a venue (both in
 * the round's single {@code venueOrLink} field). Stored as the enum name.
 */
public enum InterviewMode {
    ONLINE,
    OFFLINE
}
