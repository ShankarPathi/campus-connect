package com.campusconnect.recruiter.drives;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Create/update body for a recruiter's drive draft (Story 4.1). One shape backs both {@code POST}
 * (create) and {@code PUT} (update) — the fields are identical, so they are not duplicated. All fields
 * are optional so a {@code DRAFT} can be saved partially; the completeness gate is submission (4.2).
 * Bean-validation covers ranges/sizes; branch/batch membership in the college is checked in the
 * service ({@code package} is a Java keyword, so the CTC field is {@code packageLpa}).
 */
public record DriveRequest(
        @Size(max = 200) String role,
        @PositiveOrZero Double packageLpa,
        @Size(max = 200) String location,
        @Valid EligibilityCriteriaRequest eligibility,
        @Min(1) Integer openings,
        Instant applyDeadline) {
}
