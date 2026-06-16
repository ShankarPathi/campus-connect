package com.campusconnect.recruiter.offers;

import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * The released offer as returned to the recruiter (Story 7.1, FR-23). Carries the offer's identity, the
 * four terms, the {@code status} ({@code PENDING} on release), and a fresh 15-minute pre-signed
 * {@code offerLetterUrl} so the recruiter can verify the uploaded PDF. The internal {@code offerLetterKey}
 * is <b>never</b> exposed — {@link #of(Offer, String)} copies only safe fields and the URL is supplied by
 * the service. {@code @JsonInclude(NON_NULL)} omits the URL if absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferResponse(
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
    public static OfferResponse of(Offer offer, String offerLetterUrl) {
        return new OfferResponse(
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
