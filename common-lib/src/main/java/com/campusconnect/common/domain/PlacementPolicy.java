package com.campusconnect.common.domain;

/**
 * A college's tenant-level eligibility policy (Story 5.2, FR-14) — embedded in {@link Tenant} (small,
 * read together), replacing the placeholder {@code Map<String,Object>}. It carries exactly the three
 * FR-14 tenant-policy values; each is <b>nullable</b>, where {@code null} means "inherit the platform
 * default". The {@code PolicyResolver} merges this with each drive's {@link EligibilityCriteria} to
 * produce the {@code ResolvedPolicy} the eligibility engine consumes.
 *
 * <p>Backlog policy is deliberately <b>not</b> here — it is a per-drive concern (recruiter-set on the
 * drive's criteria); FR-14 names only the CGPA floor, the placed-student rule, and the package threshold
 * as tenant policy.
 */
public class PlacementPolicy {

    /** The college-wide minimum CGPA floor; null = no tenant floor. */
    private Double minCgpaFloor;
    /** Whether already-placed students may apply at all; null = inherit the platform default (false). */
    private Boolean placedStudentsMayApply;
    /** For placed students who may apply, the package (LPA) at/above which re-application is allowed; null = none. */
    private Double reapplyPackageThresholdLpa;

    public PlacementPolicy() {
    }

    public PlacementPolicy(Double minCgpaFloor, Boolean placedStudentsMayApply, Double reapplyPackageThresholdLpa) {
        this.minCgpaFloor = minCgpaFloor;
        this.placedStudentsMayApply = placedStudentsMayApply;
        this.reapplyPackageThresholdLpa = reapplyPackageThresholdLpa;
    }

    /** The platform baseline a tenant overrides field-by-field: no floor, placed may not apply, no threshold. */
    public static PlacementPolicy platformDefault() {
        return new PlacementPolicy(null, false, null);
    }

    public Double getMinCgpaFloor() {
        return minCgpaFloor;
    }

    public void setMinCgpaFloor(Double minCgpaFloor) {
        this.minCgpaFloor = minCgpaFloor;
    }

    public Boolean getPlacedStudentsMayApply() {
        return placedStudentsMayApply;
    }

    public void setPlacedStudentsMayApply(Boolean placedStudentsMayApply) {
        this.placedStudentsMayApply = placedStudentsMayApply;
    }

    public Double getReapplyPackageThresholdLpa() {
        return reapplyPackageThresholdLpa;
    }

    public void setReapplyPackageThresholdLpa(Double reapplyPackageThresholdLpa) {
        this.reapplyPackageThresholdLpa = reapplyPackageThresholdLpa;
    }
}
