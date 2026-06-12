package com.campusconnect.student.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * The full editable state of a student's placement profile (Story 3.1). A "save draft" {@code PUT}
 * represents the whole form, so every field is optional/nullable — partial drafts are allowed and the
 * completeness gate is enforced only at submit. Format constraints (CGPA range, non-negative backlogs)
 * are validated when a value is present; tenant-membership of branch/batch is checked in the service.
 */
public record StudentProfileRequest(
        @Valid Personal personal,
        @Valid Academic academic,
        @Valid Placement placement,
        String rollNumber,
        String batch) {

    public record Personal(String fullName, String phone, String gender, String dateOfBirth, String address) {
    }

    public record Academic(
            String branch,
            @DecimalMin(value = "0.0", message = "CGPA must be at least 0")
            @DecimalMax(value = "10.0", message = "CGPA must be at most 10")
            Double cgpa,
            @Min(value = 0, message = "Active backlogs cannot be negative")
            Integer activeBacklogs) {
    }

    public record Placement(List<String> skills, String expectedRole, String about) {
    }
}
