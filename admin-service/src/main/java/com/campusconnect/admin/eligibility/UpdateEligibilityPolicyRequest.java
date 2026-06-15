package com.campusconnect.admin.eligibility;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * A College Admin's replacement of the tenant eligibility policy (Story 5.2, FR-14). Every field is
 * nullable — {@code null} means "inherit the platform default". Ranges are validated here; the values
 * are stored verbatim on the tenant and merged with each drive's criteria by the {@code PolicyResolver}.
 */
public record UpdateEligibilityPolicyRequest(
        @DecimalMin(value = "0.0", message = "Minimum CGPA floor must be at least 0")
        @DecimalMax(value = "10.0", message = "Minimum CGPA floor must be at most 10")
        Double minCgpaFloor,

        Boolean placedStudentsMayApply,

        @DecimalMin(value = "0.0", message = "Package threshold must be at least 0")
        Double reapplyPackageThresholdLpa) {
}
