package com.campusconnect.common.eligibility;

import com.campusconnect.common.domain.BacklogPolicy;

/**
 * The <b>resolved</b> eligibility-policy values the engine reads — the effective thresholds after the
 * tenant-default ⊕ per-drive merge. Story 5.1 defines this type and {@link EligibilityEngine} consumes
 * it; the <b>merge that produces it is Story 5.2</b> (5.1's tests construct it directly).
 *
 * <p>Only the policy-governed fields live here. Branch and batch are per-drive facts read straight off
 * {@code drive.getEligibility()} (not policy-merged); CGPA floor, backlog stance, and the placed-student
 * rule <i>are</i> policy (tenant default + drive criteria), so they are resolved into this record.
 *
 * @param minCgpa                    the effective CGPA floor (max of the drive's {@code minCgpa} and the
 *                                   tenant floor); {@code null} means no floor (rule 8 passes)
 * @param backlogPolicy             the resolved backlog stance (rule 9)
 * @param placedStudentsMayApply    whether an already-placed student may apply at all (rule 10)
 * @param reapplyPackageThresholdLpa when placed students may apply, the package (LPA) at/above which a
 *                                   placed student may still apply — the "dream offer" exception (FR-14)
 */
public record ResolvedPolicy(
        Double minCgpa,
        BacklogPolicy backlogPolicy,
        boolean placedStudentsMayApply,
        Double reapplyPackageThresholdLpa) {
}
