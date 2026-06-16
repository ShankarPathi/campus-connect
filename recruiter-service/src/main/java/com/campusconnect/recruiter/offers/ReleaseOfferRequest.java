package com.campusconnect.recruiter.offers;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * The terms of an offer (Story 7.1, FR-23) — the JSON {@code data} part of the multipart release request
 * (the offer-letter PDF is the {@code file} part). Bean-validated as a {@code @Valid @RequestPart}: a blank
 * role, a non-positive CTC, or a past joining/acceptance date is a 400 {@code VALIDATION_ERROR}. The
 * cross-field rule "{@code acceptanceDeadline} before {@code joiningDate}" (you accept before you join) is
 * checked in the service.
 *
 * @param role               the offered role/title (e.g. "SDE-1")
 * @param ctc                the cost-to-company in LPA (like {@code Drive.packageLpa})
 * @param joiningDate        when the student is expected to join (must be in the future)
 * @param acceptanceDeadline by when the student must accept (must be in the future, and before joining)
 */
public record ReleaseOfferRequest(
        @NotBlank String role,
        @NotNull @Positive Double ctc,
        @NotNull @Future Instant joiningDate,
        @NotNull @Future Instant acceptanceDeadline) {
}
