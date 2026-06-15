package com.campusconnect.admin.eligibility;

import com.campusconnect.common.domain.PlacementPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The effective tenant eligibility policy returned to a College Admin (Story 5.2). Built from the
 * tenant's {@link PlacementPolicy} after the platform-default coalesce, so the admin sees the values
 * actually in force ({@code placedStudentsMayApply} is never null here). A null {@code minCgpaFloor} /
 * {@code reapplyPackageThresholdLpa} means "no floor / no threshold" — omitted from the JSON
 * ({@code NON_NULL}, mirroring the {@code ApiResponse} envelope) rather than rendered as {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EligibilityPolicyResponse(
        Double minCgpaFloor,
        boolean placedStudentsMayApply,
        Double reapplyPackageThresholdLpa) {

    public static EligibilityPolicyResponse from(PlacementPolicy p) {
        return new EligibilityPolicyResponse(
                p.getMinCgpaFloor(),
                Boolean.TRUE.equals(p.getPlacedStudentsMayApply()),
                p.getReapplyPackageThresholdLpa());
    }
}
