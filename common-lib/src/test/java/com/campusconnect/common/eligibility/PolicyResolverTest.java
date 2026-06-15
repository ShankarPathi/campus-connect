package com.campusconnect.common.eligibility;

import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.PlacementPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link PolicyResolver} (Story 5.2, FR-14) — the platform-default ⊕ tenant ⊕
 * per-drive merge that produces a {@link ResolvedPolicy}. No Spring, no Mongo.
 */
class PolicyResolverTest {

    private static EligibilityCriteria criteria(Double minCgpa, BacklogPolicy backlog) {
        EligibilityCriteria c = new EligibilityCriteria();
        c.setMinCgpa(minCgpa);
        c.setBacklogPolicy(backlog);
        return c;
    }

    private static PlacementPolicy tenant(Double floor, Boolean placedMayApply, Double threshold) {
        return new PlacementPolicy(floor, placedMayApply, threshold);
    }

    // ── minCgpa: the stricter (max) of drive floor and tenant floor ─────────────────────────────────

    @Test
    void minCgpa_tenantFloorStricter_winsOverDrive() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(7.5, null, null), criteria(7.0, null));
        assertThat(r.minCgpa()).isEqualTo(7.5);
    }

    @Test
    void minCgpa_driveFloorStricter_winsOverTenant() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(7.0, null, null), criteria(8.0, null));
        assertThat(r.minCgpa()).isEqualTo(8.0);
    }

    @Test
    void minCgpa_driveNull_usesTenantFloor() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(6.5, null, null), criteria(null, null));
        assertThat(r.minCgpa()).isEqualTo(6.5);
    }

    @Test
    void minCgpa_bothNull_noFloor() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, null, null), criteria(null, null));
        assertThat(r.minCgpa()).isNull();
    }

    @Test
    void minCgpa_tenantNull_usesDriveFloor() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, null, null), criteria(7.0, null));
        assertThat(r.minCgpa()).isEqualTo(7.0);
    }

    // ── backlogPolicy: per-drive only ───────────────────────────────────────────────────────────────

    @Test
    void backlog_takenFromDrive() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, null, null), criteria(null, BacklogPolicy.NO_BACKLOG));
        assertThat(r.backlogPolicy()).isEqualTo(BacklogPolicy.NO_BACKLOG);
    }

    @Test
    void backlog_driveNull_defaultsToAllow() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, null, null), criteria(null, null));
        assertThat(r.backlogPolicy()).isEqualTo(BacklogPolicy.ALLOW_BACKLOG);
    }

    // ── placedStudentsMayApply: tenant value, else platform default (false) ─────────────────────────

    @Test
    void placed_tenantOptsIn_true() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, true, null), criteria(null, null));
        assertThat(r.placedStudentsMayApply()).isTrue();
    }

    @Test
    void placed_tenantNull_inheritsDefaultFalse() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, null, null), criteria(null, null));
        assertThat(r.placedStudentsMayApply()).isFalse();
    }

    @Test
    void placed_tenantExplicitFalse_false() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, false, null), criteria(null, null));
        assertThat(r.placedStudentsMayApply()).isFalse();
    }

    // ── reapplyPackageThresholdLpa: tenant value, else default (null) ────────────────────────────────

    @Test
    void threshold_tenantValue_used() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, true, 15.0), criteria(null, null));
        assertThat(r.reapplyPackageThresholdLpa()).isEqualTo(15.0);
    }

    @Test
    void threshold_tenantNull_inheritsDefaultNull() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(null, true, null), criteria(null, null));
        assertThat(r.reapplyPackageThresholdLpa()).isNull();
    }

    // ── null inputs ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void nullTenantPolicy_isPlatformDefault() {
        ResolvedPolicy r = PolicyResolver.resolve(null, criteria(7.0, BacklogPolicy.NO_BACKLOG));
        assertThat(r.minCgpa()).isEqualTo(7.0);
        assertThat(r.backlogPolicy()).isEqualTo(BacklogPolicy.NO_BACKLOG);
        assertThat(r.placedStudentsMayApply()).isFalse();
        assertThat(r.reapplyPackageThresholdLpa()).isNull();
    }

    @Test
    void nullDriveCriteria_usesTenantFloorAndAllowBacklog() {
        ResolvedPolicy r = PolicyResolver.resolve(tenant(6.0, true, 20.0), null);
        assertThat(r.minCgpa()).isEqualTo(6.0);
        assertThat(r.backlogPolicy()).isEqualTo(BacklogPolicy.ALLOW_BACKLOG);
        assertThat(r.placedStudentsMayApply()).isTrue();
        assertThat(r.reapplyPackageThresholdLpa()).isEqualTo(20.0);
    }

    @Test
    void bothNull_isFullPlatformDefault() {
        ResolvedPolicy r = PolicyResolver.resolve(null, null);
        assertThat(r.minCgpa()).isNull();
        assertThat(r.backlogPolicy()).isEqualTo(BacklogPolicy.ALLOW_BACKLOG);
        assertThat(r.placedStudentsMayApply()).isFalse();
        assertThat(r.reapplyPackageThresholdLpa()).isNull();
    }

    // ── effectiveTenantPolicy (the admin GET view) ──────────────────────────────────────────────────

    @Test
    void effectiveTenantPolicy_nullTenant_isDefault() {
        PlacementPolicy p = PolicyResolver.effectiveTenantPolicy(null);
        assertThat(p.getMinCgpaFloor()).isNull();
        assertThat(p.getPlacedStudentsMayApply()).isFalse();
        assertThat(p.getReapplyPackageThresholdLpa()).isNull();
    }

    @Test
    void effectiveTenantPolicy_partial_coalescesNullsToDefault() {
        PlacementPolicy p = PolicyResolver.effectiveTenantPolicy(tenant(7.0, null, null));
        assertThat(p.getMinCgpaFloor()).isEqualTo(7.0);       // tenant value preserved
        assertThat(p.getPlacedStudentsMayApply()).isFalse();  // null → default
        assertThat(p.getReapplyPackageThresholdLpa()).isNull();
    }
}
