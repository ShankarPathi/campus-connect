package com.campusconnect.common.domain;

/**
 * The lifecycle of an {@link EmailOutbox} row (Story 8.2, FR-28/NFR-5). A freshly enqueued email is
 * {@code PENDING}; the {@code email-outbox-flush} job sends it and marks it {@code SENT}, or — after
 * {@code MAX_ATTEMPTS} failed sends — {@code FAILED} (a dead-letter, retained for diagnosis, never re-swept).
 */
public enum EmailStatus {
    PENDING,
    SENT,
    FAILED
}
