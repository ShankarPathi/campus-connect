package com.campusconnect.admin.profiles;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * A College Admin's correction of a student's eligibility-critical academic fields (Story 3.3). Every
 * field is nullable — only the provided ones are applied (e.g. a lone CGPA correction). Format
 * constraints are validated when present; tenant-membership of branch/batch is checked in the service.
 */
public record AdminEditProfileRequest(
        String branch,
        @DecimalMin(value = "0.0", message = "CGPA must be at least 0")
        @DecimalMax(value = "10.0", message = "CGPA must be at most 10")
        Double cgpa,
        @Min(value = 0, message = "Active backlogs cannot be negative")
        Integer activeBacklogs,
        String batch) {
}
