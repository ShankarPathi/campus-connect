package com.campusconnect.admin.drives;

import com.campusconnect.common.domain.BacklogPolicy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A College Admin's correction of a drive's eligibility criteria (Story 4.3). Every field is nullable —
 * only the provided ones are applied. Ranges/sizes are validated here; branch/batch membership in the
 * college is checked in the service. The drive's status is unchanged by an edit.
 */
public record AdminEditDriveCriteriaRequest(
        @Size(max = 50) List<@Size(max = 64) String> branches,
        @DecimalMin(value = "0.0", message = "Minimum CGPA must be at least 0")
        @DecimalMax(value = "10.0", message = "Minimum CGPA must be at most 10")
        Double minCgpa,
        BacklogPolicy backlogPolicy,
        @Size(max = 20) String batch) {
}
