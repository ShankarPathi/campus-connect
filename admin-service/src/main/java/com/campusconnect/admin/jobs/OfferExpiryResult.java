package com.campusconnect.admin.jobs;

/**
 * The summary of one offer-expiry run (Story 7.2) — {@code expiredCount} offers transitioned
 * {@code PENDING → EXPIRED} (with their applications {@code OFFER_RELEASED → OFFER_EXPIRED}), and
 * {@code failedCount} offers skipped after a per-offer error. Logged at INFO by the job.
 */
public record OfferExpiryResult(int expiredCount, int failedCount) {
}
