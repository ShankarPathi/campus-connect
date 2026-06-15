package com.campusconnect.student.drives;

import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * One drive on the student's pre-apply transparency list (Story 5.3, FR-13): the drive's display fields
 * plus its eligibility {@link EligibilityGroup} and, for a {@code NOT_ELIGIBLE} drive, the specific
 * failed criteria (the per-rule reasons) — never a generic block. {@code failedCriteria} is empty for
 * every other group. {@code NON_NULL} omits absent display fields (mirroring the {@code ApiResponse}
 * envelope).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudentDriveResponse(
        String id,
        String companyName,
        String role,
        Double packageLpa,
        String location,
        Instant applyDeadline,
        DriveStatus status,
        EligibilityGroup group,
        List<String> failedCriteria) {

    public static StudentDriveResponse of(Drive d, EligibilityGroup group, List<String> failedCriteria) {
        return new StudentDriveResponse(
                d.getId(), d.getCompanyName(), d.getRole(), d.getPackageLpa(), d.getLocation(),
                d.getApplyDeadline(), d.getStatus(), group, failedCriteria);
    }
}
