package com.campusconnect.common.eligibility;

import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.PlacementPolicy;

/**
 * The pure two-layer policy merge (Story 5.2, FR-14) — turns a tenant's {@link PlacementPolicy} and a
 * drive's {@link EligibilityCriteria} into the {@link ResolvedPolicy} the {@link EligibilityEngine}
 * consumes. No Spring, no DB: the caller loads the {@code Tenant} and {@code Drive} and hands this
 * resolver their two embedded sub-objects, exactly as the engine takes the clock as an input.
 *
 * <p>The merge (architecture §6):
 * <ul>
 *   <li><b>minCgpa</b> (effective floor) = the <i>stricter</i> (max) of the drive's {@code minCgpa} and
 *       the tenant's {@code minCgpaFloor}; null-safe (both null → no floor).</li>
 *   <li><b>backlogPolicy</b> = <b>per-drive</b> ({@code driveCriteria.backlogPolicy}, default
 *       {@link BacklogPolicy#ALLOW_BACKLOG}); not a tenant-policy value.</li>
 *   <li><b>placedStudentsMayApply</b> = the tenant value, else the platform default ({@code false}).</li>
 *   <li><b>reapplyPackageThresholdLpa</b> = the tenant value, else the platform default ({@code null}).</li>
 * </ul>
 */
public final class PolicyResolver {

    private PolicyResolver() {
    }

    /** Merge platform default ⊕ tenant policy ⊕ per-drive criteria into the engine's {@link ResolvedPolicy}. */
    public static ResolvedPolicy resolve(PlacementPolicy tenantPolicy, EligibilityCriteria driveCriteria) {
        PlacementPolicy tenant = effectiveTenantPolicy(tenantPolicy);

        Double driveFloor = driveCriteria == null ? null : driveCriteria.getMinCgpa();
        Double effectiveFloor = stricter(driveFloor, tenant.getMinCgpaFloor());

        BacklogPolicy backlog = driveCriteria == null ? null : driveCriteria.getBacklogPolicy();
        if (backlog == null) {
            backlog = BacklogPolicy.ALLOW_BACKLOG;
        }

        return new ResolvedPolicy(
                effectiveFloor,
                backlog,
                Boolean.TRUE.equals(tenant.getPlacedStudentsMayApply()),
                tenant.getReapplyPackageThresholdLpa());
    }

    /**
     * The tenant-level effective policy: each null field of {@code tenantPolicy} coalesced to the platform
     * default (override-vs-inherit, FR-14). A null {@code tenantPolicy} is the platform default outright.
     * This is the tenant view (no per-drive merge) — used by the admin GET to show the effective values.
     */
    public static PlacementPolicy effectiveTenantPolicy(PlacementPolicy tenantPolicy) {
        PlacementPolicy def = PlacementPolicy.platformDefault();
        if (tenantPolicy == null) {
            return def;
        }
        Double floor = tenantPolicy.getMinCgpaFloor() != null ? tenantPolicy.getMinCgpaFloor() : def.getMinCgpaFloor();
        Boolean placed = tenantPolicy.getPlacedStudentsMayApply() != null
                ? tenantPolicy.getPlacedStudentsMayApply() : def.getPlacedStudentsMayApply();
        Double threshold = tenantPolicy.getReapplyPackageThresholdLpa() != null
                ? tenantPolicy.getReapplyPackageThresholdLpa() : def.getReapplyPackageThresholdLpa();
        return new PlacementPolicy(floor, placed, threshold);
    }

    /** The stricter (max) of two nullable CGPA floors; null means "no floor on that side". */
    private static Double stricter(Double a, Double b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }
}
