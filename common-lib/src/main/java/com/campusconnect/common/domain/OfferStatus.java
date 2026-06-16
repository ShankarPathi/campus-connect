package com.campusconnect.common.domain;

/**
 * The lifecycle states of an {@link Offer} (architecture §8, Epic 7). Story 7.1 only ever creates an offer
 * in {@code PENDING}; the transitions land with the first offer state change — {@code PENDING → EXPIRED}
 * (the 7.2 expiry job) and {@code PENDING → ACCEPTED|DECLINED} (the 7.3 student response) — at which point
 * the canonical {@code OfferLifecycle} is introduced (mirroring how {@code ApplicationLifecycle} arrived at
 * Story 5.5, not at the entity's creation). Stored as the enum name in the document.
 */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}
