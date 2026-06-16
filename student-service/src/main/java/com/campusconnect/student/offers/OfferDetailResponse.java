package com.campusconnect.student.offers;

import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One of the student's offers in detail (Story 7.3, FR-24) — the terms, status, and a fresh 15-minute
 * pre-signed {@code offerLetterUrl} to view/download the offer-letter PDF. The internal {@code offerLetterKey}
 * is <b>never</b> exposed — {@link #of(Offer, String)} copies only safe fields and the URL is supplied by the
 * service. {@code @JsonInclude(NON_NULL)} omits the URL when absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferDetailResponse(
        String id,
        String applicationId,
        String studentId,
        String role,
        Double ctc,
        Instant joiningDate,
        Instant acceptanceDeadline,
        OfferStatus status,
        String offerLetterUrl) {

    /** Projects a persisted {@link Offer} plus a freshly-signed download URL — never copies {@code offerLetterKey}. */
    public static OfferDetailResponse of(Offer offer, String offerLetterUrl) {
        return new OfferDetailResponse(
                offer.getId(),
                offer.getApplicationId(),
                offer.getStudentId(),
                offer.getRole(),
                offer.getCtc(),
                offer.getJoiningDate(),
                offer.getAcceptanceDeadline(),
                offer.getStatus(),
                offerLetterUrl);
    }
}
