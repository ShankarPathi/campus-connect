package com.campusconnect.admin.placements;

import com.campusconnect.common.domain.PlacementRecord;
import com.campusconnect.common.domain.PlacementStatus;

import java.time.Instant;

/**
 * A placement record as the College Admin sees it (Story 7.4, FR-25) — the confirmation queue and the
 * confirmed result. Carries the denormalized placement terms the admin verifies and that reports count.
 */
public record PlacementResponse(
        String id,
        String studentId,
        String applicationId,
        String company,
        Double ctc,
        String role,
        Instant joiningDate,
        PlacementStatus status) {

    public static PlacementResponse of(PlacementRecord record) {
        return new PlacementResponse(
                record.getId(),
                record.getStudentId(),
                record.getApplicationId(),
                record.getCompany(),
                record.getCtc(),
                record.getRole(),
                record.getJoiningDate(),
                record.getStatus());
    }
}
