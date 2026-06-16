package com.campusconnect.student.offers;

import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferStatus;

import java.time.Instant;

/**
 * One of the student's offers in list form (Story 7.3, FR-24) — the terms + status, no PDF. The
 * {@code offerLetterKey} is internal and never projected; the download URL is served only by the
 * detail view ({@link OfferDetailResponse}).
 */
public record OfferSummaryResponse(
        String id,
        String applicationId,
        String role,
        Double ctc,
        Instant joiningDate,
        Instant acceptanceDeadline,
        OfferStatus status) {

    public static OfferSummaryResponse of(Offer offer) {
        return new OfferSummaryResponse(
                offer.getId(),
                offer.getApplicationId(),
                offer.getRole(),
                offer.getCtc(),
                offer.getJoiningDate(),
                offer.getAcceptanceDeadline(),
                offer.getStatus());
    }
}
