package com.campusconnect.common.eligibility;

import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.PlacementPolicy;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.AccountStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolver + engine together (Story 5.2, AC 6) — proves the tenant policy a {@link PolicyResolver}
 * produces actually changes the {@link EligibilityEngine} verdict. Both are pure {@code common-lib}
 * classes, so this stays a fast pure test.
 */
class ResolverEngineIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");

    private static StudentProfile profile(Double cgpa, boolean placed) {
        StudentProfile p = new StudentProfile();
        p.setProfileApprovalStatus(ProfileApprovalStatus.APPROVED);
        p.setBatch("2025");
        p.getAcademic().setBranch("CSE");
        p.getAcademic().setCgpa(cgpa);
        p.getAcademic().setActiveBacklogs(0);
        p.setPlaced(placed);
        return p;
    }

    private static Drive drive(Double driveMinCgpa, Double packageLpa) {
        Drive d = new Drive();
        d.setStatus(DriveStatus.PUBLISHED);
        d.setApplyDeadline(NOW.plusSeconds(86_400));
        d.setPackageLpa(packageLpa);
        d.getEligibility().setBranches(new java.util.ArrayList<>(List.of("CSE")));
        d.getEligibility().setBatch("2025");
        d.getEligibility().setMinCgpa(driveMinCgpa);
        d.getEligibility().setBacklogPolicy(BacklogPolicy.ALLOW_BACKLOG);
        return d;
    }

    private static EligibilityContext ctx(StudentProfile p, Drive d, PlacementPolicy tenantPolicy) {
        ResolvedPolicy resolved = PolicyResolver.resolve(tenantPolicy, d.getEligibility());
        return new EligibilityContext(AccountStatus.ACTIVE, p, d, resolved, false, NOW);
    }

    @Test
    void tenantFloorRaisesDriveFloor_midCgpaNowFails() {
        // Drive floor 7.0 would pass a 7.2 CGPA; the tenant floor 7.5 raises it → CGPA_MET fails.
        Drive d = drive(7.0, 12.0);
        PlacementPolicy tenant = new PlacementPolicy(7.5, null, null);
        EligibilityResult result = EligibilityEngine.check(ctx(profile(7.2, false), d, tenant));
        assertThat(result.failedRule()).isEqualTo(RuleId.CGPA_MET);
        assertThat(result.reason()).contains("7.5");
    }

    @Test
    void noTenantFloor_drivePasses() {
        Drive d = drive(7.0, 12.0);
        EligibilityResult result = EligibilityEngine.check(ctx(profile(7.2, false), d, new PlacementPolicy()));
        assertThat(result.eligible()).isTrue();
    }

    @Test
    void placedStudent_defaultPolicy_fails() {
        // Tenant has not opted in → placed students may not apply.
        Drive d = drive(7.0, 20.0);
        EligibilityResult result = EligibilityEngine.check(ctx(profile(8.0, true), d, new PlacementPolicy()));
        assertThat(result.failedRule()).isEqualTo(RuleId.PLACEMENT_RESTRICTION);
    }

    @Test
    void placedStudent_optInAndPackageClearsThreshold_passes() {
        Drive d = drive(7.0, 20.0);
        PlacementPolicy tenant = new PlacementPolicy(null, true, 15.0);
        EligibilityResult result = EligibilityEngine.check(ctx(profile(8.0, true), d, tenant));
        assertThat(result.eligible()).isTrue();
    }

    @Test
    void placedStudent_optInButPackageBelowThreshold_fails() {
        Drive d = drive(7.0, 12.0);
        PlacementPolicy tenant = new PlacementPolicy(null, true, 15.0);
        EligibilityResult result = EligibilityEngine.check(ctx(profile(8.0, true), d, tenant));
        assertThat(result.failedRule()).isEqualTo(RuleId.PLACEMENT_RESTRICTION);
    }
}
