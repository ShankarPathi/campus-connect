package com.campusconnect.recruiter.drives;

import com.campusconnect.common.domain.BacklogPolicy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.util.List;

// @Size bounds the list itself (not just each element) so a client cannot post an unbounded branch list.

/**
 * Eligibility criteria sub-request of a drive (Story 4.1). All fields are optional — a {@code DRAFT}
 * may be partially filled; the "all criteria required" gate is submission (Story 4.2). Ranges are
 * validated here; branch/batch <i>membership</i> in the college is validated in the service.
 */
public record EligibilityCriteriaRequest(
        @Size(max = 50) List<@Size(max = 64) String> branches,
        @DecimalMin("0.0") @DecimalMax("10.0") Double minCgpa,
        BacklogPolicy backlogPolicy,
        @Size(max = 20) String batch) {
}
