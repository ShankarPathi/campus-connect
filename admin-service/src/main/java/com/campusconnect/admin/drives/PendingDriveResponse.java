package com.campusconnect.admin.drives;

import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.Drive;

import java.time.Instant;
import java.util.List;

/**
 * A College Admin's review view of a drive (Story 4.3) — the fields needed to decide publication.
 * Exposes no {@code tenantId}; {@code rejectionReason} is null unless the drive is {@code REJECTED_BY_ADMIN}.
 */
public record PendingDriveResponse(
        String id,
        String companyName,
        String role,
        Double packageLpa,
        String location,
        Eligibility eligibility,
        Integer openings,
        Instant applyDeadline,
        String status,
        String rejectionReason) {

    public record Eligibility(List<String> branches, Double minCgpa, BacklogPolicy backlogPolicy, String batch) {
    }

    public static PendingDriveResponse of(Drive d) {
        var e = d.getEligibility();
        return new PendingDriveResponse(
                d.getId(),
                d.getCompanyName(),
                d.getRole(),
                d.getPackageLpa(),
                d.getLocation(),
                new Eligibility(e.getBranches(), e.getMinCgpa(), e.getBacklogPolicy(), e.getBatch()),
                d.getOpenings(),
                d.getApplyDeadline(),
                d.getStatus().name(),
                d.getRejectionReason());
    }
}
