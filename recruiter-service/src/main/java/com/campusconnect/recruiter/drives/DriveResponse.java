package com.campusconnect.recruiter.drives;

import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.Drive;

import java.time.Instant;
import java.util.List;

/**
 * The recruiter's view of one of their drives (Story 4.1). Exposes the editable fields, the
 * snapshotted {@code companyName}, and the {@code status} — never {@code tenantId} or {@code createdBy}.
 */
public record DriveResponse(
        String id,
        String companyName,
        String role,
        Double packageLpa,
        String location,
        Eligibility eligibility,
        Integer openings,
        Instant applyDeadline,
        String status) {

    public record Eligibility(List<String> branches, Double minCgpa, BacklogPolicy backlogPolicy, String batch) {
    }

    public static DriveResponse of(Drive d) {
        var e = d.getEligibility();
        return new DriveResponse(
                d.getId(),
                d.getCompanyName(),
                d.getRole(),
                d.getPackageLpa(),
                d.getLocation(),
                new Eligibility(e.getBranches(), e.getMinCgpa(), e.getBacklogPolicy(), e.getBatch()),
                d.getOpenings(),
                d.getApplyDeadline(),
                d.getStatus().name());
    }
}
