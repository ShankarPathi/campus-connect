package com.campusconnect.common.eligibility;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link EligibilityEngine} (Story 5.1, FR-12) — no Spring, no Mongo, the fastest
 * test class in the codebase. Each rule has a {@code _pass} and a {@code _fail} test built by mutating
 * exactly one field of an all-pass baseline ({@link #validContext()}), plus all-pass, short-circuit-order,
 * null-safety, and the placement-threshold boundary.
 */
class EligibilityEngineTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final Instant FUTURE_DEADLINE = NOW.plusSeconds(86_400);

    // ── Baseline builders (all-pass) ────────────────────────────────────────────────────────────────

    private static StudentProfile validProfile() {
        StudentProfile p = new StudentProfile();
        p.setProfileApprovalStatus(ProfileApprovalStatus.APPROVED);
        p.setBatch("2025");
        p.getAcademic().setBranch("CSE");
        p.getAcademic().setCgpa(8.0);
        p.getAcademic().setActiveBacklogs(0);
        p.setPlaced(false);
        return p;
    }

    private static Drive validDrive() {
        Drive d = new Drive();
        d.setStatus(DriveStatus.PUBLISHED);
        d.setApplyDeadline(FUTURE_DEADLINE);
        d.setPackageLpa(12.0);
        d.getEligibility().setBranches(new java.util.ArrayList<>(List.of("CSE", "ECE")));
        d.getEligibility().setBatch("2025");
        d.getEligibility().setMinCgpa(7.0);
        d.getEligibility().setBacklogPolicy(BacklogPolicy.NO_BACKLOG);
        return d;
    }

    /** Resolved policy that, on the baseline, lets the student through (no placement re-apply needed). */
    private static ResolvedPolicy validPolicy() {
        return new ResolvedPolicy(7.0, BacklogPolicy.NO_BACKLOG, false, null);
    }

    private static EligibilityContext validContext() {
        return new EligibilityContext(AccountStatus.ACTIVE, validProfile(), validDrive(), validPolicy(), false, NOW);
    }

    // ── Rule 1: ACCOUNT_ACTIVE ─────────────────────────────────────────────────────────────────────

    @Test
    void accountActive_pass() {
        assertThat(EligibilityEngine.check(validContext()).eligible()).isTrue();
    }

    @Test
    void accountActive_fail() {
        EligibilityContext ctx = new EligibilityContext(
                AccountStatus.DEACTIVATED, validProfile(), validDrive(), validPolicy(), false, NOW);
        assertFailedRule(ctx, RuleId.ACCOUNT_ACTIVE);
    }

    // ── Rule 2: PROFILE_APPROVED ───────────────────────────────────────────────────────────────────

    @Test
    void profileApproved_pass() {
        StudentProfile p = validProfile();
        p.setProfileApprovalStatus(ProfileApprovalStatus.APPROVED);
        assertThat(EligibilityEngine.check(contextWithProfile(p)).eligible()).isTrue();
    }

    @Test
    void profileApproved_fail() {
        StudentProfile p = validProfile();
        p.setProfileApprovalStatus(ProfileApprovalStatus.PENDING_APPROVAL);
        assertFailedRule(contextWithProfile(p), RuleId.PROFILE_APPROVED);
    }

    // ── Rule 3: DRIVE_OPEN ─────────────────────────────────────────────────────────────────────────

    @Test
    void driveOpen_pass_published() {
        Drive d = validDrive();
        d.setStatus(DriveStatus.PUBLISHED);
        assertThat(EligibilityEngine.check(contextWithDrive(d)).eligible()).isTrue();
    }

    @Test
    void driveOpen_pass_ongoing() {
        Drive d = validDrive();
        d.setStatus(DriveStatus.ONGOING);
        assertThat(EligibilityEngine.check(contextWithDrive(d)).eligible()).isTrue();
    }

    @Test
    void driveOpen_fail() {
        Drive d = validDrive();
        d.setStatus(DriveStatus.DRAFT);
        assertFailedRule(contextWithDrive(d), RuleId.DRIVE_OPEN);
    }

    // ── Rule 4: WITHIN_DEADLINE ────────────────────────────────────────────────────────────────────

    @Test
    void withinDeadline_pass() {
        assertThat(EligibilityEngine.check(validContext()).eligible()).isTrue();
    }

    @Test
    void withinDeadline_fail() {
        Drive d = validDrive();
        d.setApplyDeadline(NOW.minusSeconds(1));
        assertFailedRule(contextWithDrive(d), RuleId.WITHIN_DEADLINE);
    }

    // ── Rule 5: NO_DUPLICATE_APPLICATION ───────────────────────────────────────────────────────────

    @Test
    void noDuplicate_pass() {
        assertThat(EligibilityEngine.check(validContext()).eligible()).isTrue();
    }

    @Test
    void noDuplicate_fail() {
        EligibilityContext ctx = new EligibilityContext(
                AccountStatus.ACTIVE, validProfile(), validDrive(), validPolicy(), true, NOW);
        assertFailedRule(ctx, RuleId.NO_DUPLICATE_APPLICATION);
    }

    // ── Rule 6: BATCH_MATCH ────────────────────────────────────────────────────────────────────────

    @Test
    void batchMatch_pass() {
        StudentProfile p = validProfile();
        p.setBatch("2025");
        assertThat(EligibilityEngine.check(contextWithProfile(p)).eligible()).isTrue();
    }

    @Test
    void batchMatch_pass_whenDriveBatchBlank() {
        Drive d = validDrive();
        d.getEligibility().setBatch(null);
        assertThat(EligibilityEngine.check(contextWithDrive(d)).eligible()).isTrue();
    }

    @Test
    void batchMatch_fail() {
        StudentProfile p = validProfile();
        p.setBatch("2024");
        assertFailedRule(contextWithProfile(p), RuleId.BATCH_MATCH);
    }

    // ── Rule 7: BRANCH_ELIGIBLE ────────────────────────────────────────────────────────────────────

    @Test
    void branchEligible_pass() {
        StudentProfile p = validProfile();
        p.getAcademic().setBranch("ECE");
        assertThat(EligibilityEngine.check(contextWithProfile(p)).eligible()).isTrue();
    }

    @Test
    void branchEligible_pass_whenDriveBranchesEmpty() {
        Drive d = validDrive();
        d.getEligibility().setBranches(new java.util.ArrayList<>());
        assertThat(EligibilityEngine.check(contextWithDrive(d)).eligible()).isTrue();
    }

    @Test
    void branchEligible_fail() {
        StudentProfile p = validProfile();
        p.getAcademic().setBranch("MECH");
        assertFailedRule(contextWithProfile(p), RuleId.BRANCH_ELIGIBLE);
    }

    // ── Rule 8: CGPA_MET ───────────────────────────────────────────────────────────────────────────

    @Test
    void cgpaMet_pass() {
        StudentProfile p = validProfile();
        p.getAcademic().setCgpa(7.0); // exactly the floor passes
        assertThat(EligibilityEngine.check(contextWithProfile(p)).eligible()).isTrue();
    }

    @Test
    void cgpaMet_pass_whenNoFloor() {
        EligibilityContext ctx = new EligibilityContext(AccountStatus.ACTIVE, validProfile(), validDrive(),
                new ResolvedPolicy(null, BacklogPolicy.NO_BACKLOG, false, null), false, NOW);
        assertThat(EligibilityEngine.check(ctx).eligible()).isTrue();
    }

    @Test
    void cgpaMet_fail() {
        StudentProfile p = validProfile();
        p.getAcademic().setCgpa(6.4);
        EligibilityResult result = EligibilityEngine.check(contextWithProfile(p));
        assertThat(result.failedRule()).isEqualTo(RuleId.CGPA_MET);
        assertThat(result.reason()).contains("6.4").contains("7.0");
    }

    // ── Rule 9: BACKLOG_POLICY ─────────────────────────────────────────────────────────────────────

    @Test
    void backlogPolicy_pass_whenNoBacklogsAndPolicyForbids() {
        StudentProfile p = validProfile();
        p.getAcademic().setActiveBacklogs(0);
        assertThat(EligibilityEngine.check(contextWithProfile(p)).eligible()).isTrue();
    }

    @Test
    void backlogPolicy_pass_whenPolicyAllows() {
        EligibilityContext ctx = new EligibilityContext(AccountStatus.ACTIVE, backlogProfile(2), validDrive(),
                new ResolvedPolicy(7.0, BacklogPolicy.ALLOW_BACKLOG, false, null), false, NOW);
        assertThat(EligibilityEngine.check(ctx).eligible()).isTrue();
    }

    @Test
    void backlogPolicy_fail() {
        EligibilityContext ctx = new EligibilityContext(AccountStatus.ACTIVE, backlogProfile(2), validDrive(),
                validPolicy(), false, NOW);
        assertFailedRule(ctx, RuleId.BACKLOG_POLICY);
    }

    // ── Rule 10: PLACEMENT_RESTRICTION ─────────────────────────────────────────────────────────────

    @Test
    void placementRestriction_pass_whenNotPlaced() {
        StudentProfile p = validProfile();
        p.setPlaced(false);
        assertThat(EligibilityEngine.check(contextWithProfile(p)).eligible()).isTrue();
    }

    @Test
    void placementRestriction_fail_whenPlacedAndPolicyForbids() {
        StudentProfile p = validProfile();
        p.setPlaced(true);
        assertFailedRule(contextWithProfile(p), RuleId.PLACEMENT_RESTRICTION);
    }

    @Test
    void placementRestriction_aboveThreshold_passes() {
        Drive d = validDrive();
        d.setPackageLpa(20.0);
        EligibilityContext ctx = new EligibilityContext(AccountStatus.ACTIVE, placedProfile(), d,
                new ResolvedPolicy(7.0, BacklogPolicy.NO_BACKLOG, true, 15.0), false, NOW);
        assertThat(EligibilityEngine.check(ctx).eligible()).isTrue();
    }

    @Test
    void placementRestriction_atThreshold_passes() {
        Drive d = validDrive();
        d.setPackageLpa(15.0); // exactly the threshold passes
        EligibilityContext ctx = new EligibilityContext(AccountStatus.ACTIVE, placedProfile(), d,
                new ResolvedPolicy(7.0, BacklogPolicy.NO_BACKLOG, true, 15.0), false, NOW);
        assertThat(EligibilityEngine.check(ctx).eligible()).isTrue();
    }

    @Test
    void placementRestriction_belowThreshold_failsPlacement() {
        Drive d = validDrive();
        d.setPackageLpa(12.0); // below the threshold
        EligibilityContext ctx = new EligibilityContext(AccountStatus.ACTIVE, placedProfile(), d,
                new ResolvedPolicy(7.0, BacklogPolicy.NO_BACKLOG, true, 15.0), false, NOW);
        assertFailedRule(ctx, RuleId.PLACEMENT_RESTRICTION);
    }

    // ── Integration / cross-cutting ────────────────────────────────────────────────────────────────

    @Test
    void check_allRulesPass_returnsPass() {
        EligibilityResult result = EligibilityEngine.check(validContext());
        assertThat(result.eligible()).isTrue();
        assertThat(result.failedRule()).isNull();
        assertThat(result.reason()).isNull();
    }

    @Test
    void check_shortCircuits_returnsFirstFailure() {
        // Fail rule 6 (batch) AND rule 8 (cgpa) — expect the earlier one, never evaluating cgpa.
        StudentProfile p = validProfile();
        p.setBatch("2024");          // rule 6 fails
        p.getAcademic().setCgpa(6.4); // rule 8 would also fail
        EligibilityResult result = EligibilityEngine.check(contextWithProfile(p));
        assertThat(result.failedRule()).isEqualTo(RuleId.BATCH_MATCH);
    }

    @Test
    void check_nullCgpa_failsCgpaRule_noNpe() {
        StudentProfile p = validProfile();
        p.getAcademic().setCgpa(null);
        EligibilityResult result = EligibilityEngine.check(contextWithProfile(p));
        assertThat(result.failedRule()).isEqualTo(RuleId.CGPA_MET);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void check_nullBranch_failsBranchRule_noNpe() {
        StudentProfile p = validProfile();
        p.getAcademic().setBranch(null);
        EligibilityResult result = EligibilityEngine.check(contextWithProfile(p));
        assertThat(result.failedRule()).isEqualTo(RuleId.BRANCH_ELIGIBLE);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void check_nullActiveBacklogs_failsBacklogRule_noNpe() {
        StudentProfile p = validProfile();
        p.getAcademic().setActiveBacklogs(null);
        EligibilityResult result = EligibilityEngine.check(contextWithProfile(p));
        assertThat(result.failedRule()).isEqualTo(RuleId.BACKLOG_POLICY);
        assertThat(result.reason()).isNotBlank();
    }

    // ── Defensive null-context safety (the "total null-safe" guarantee) ─────────────────────────────

    @Test
    void check_nullDrive_failsDriveOpenRule_noNpe() {
        EligibilityContext ctx = new EligibilityContext(
                AccountStatus.ACTIVE, validProfile(), null, validPolicy(), false, NOW);
        EligibilityResult result = EligibilityEngine.check(ctx);
        assertThat(result.failedRule()).isEqualTo(RuleId.DRIVE_OPEN);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void check_nullProfile_failsProfileApprovedRule_noNpe() {
        EligibilityContext ctx = new EligibilityContext(
                AccountStatus.ACTIVE, null, validDrive(), validPolicy(), false, NOW);
        EligibilityResult result = EligibilityEngine.check(ctx);
        assertThat(result.failedRule()).isEqualTo(RuleId.PROFILE_APPROVED);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void check_nullResolvedPolicy_treatsAsNoConstraints_noNpe() {
        // null policy → no CGPA floor, no backlog gate, no placement allowance needed (baseline not placed)
        EligibilityContext ctx = new EligibilityContext(
                AccountStatus.ACTIVE, validProfile(), validDrive(), null, false, NOW);
        assertThat(EligibilityEngine.check(ctx).eligible()).isTrue();
    }

    @Test
    void check_nullNow_failsWithinDeadlineRule_noNpe() {
        EligibilityContext ctx = new EligibilityContext(
                AccountStatus.ACTIVE, validProfile(), validDrive(), validPolicy(), false, null);
        EligibilityResult result = EligibilityEngine.check(ctx);
        assertThat(result.failedRule()).isEqualTo(RuleId.WITHIN_DEADLINE);
        assertThat(result.reason()).isNotBlank();
    }

    // ── Boundary + policy-precedence coverage ───────────────────────────────────────────────────────

    @Test
    void withinDeadline_pass_atExactDeadline() {
        // Inclusive boundary: now == deadline passes (!now.isAfter(deadline)).
        Drive d = validDrive();
        d.setApplyDeadline(NOW);
        assertThat(EligibilityEngine.check(contextWithDrive(d)).eligible()).isTrue();
    }

    @Test
    void placementRestriction_pass_whenPlacedAllowedAndNullThreshold() {
        // placedStudentsMayApply with no package gate (threshold null) → placed student passes regardless of package.
        EligibilityContext ctx = new EligibilityContext(AccountStatus.ACTIVE, placedProfile(), validDrive(),
                new ResolvedPolicy(7.0, BacklogPolicy.NO_BACKLOG, true, null), false, NOW);
        assertThat(EligibilityEngine.check(ctx).eligible()).isTrue();
    }

    @Test
    void placementRestriction_fail_whenPolicyForbids_ignoresThreshold() {
        // placedStudentsMayApply == false wins even when the drive's package clears a (contradictory) threshold.
        Drive d = validDrive();
        d.setPackageLpa(20.0); // above the threshold below
        EligibilityContext ctx = new EligibilityContext(AccountStatus.ACTIVE, placedProfile(), d,
                new ResolvedPolicy(7.0, BacklogPolicy.NO_BACKLOG, false, 15.0), false, NOW);
        assertFailedRule(ctx, RuleId.PLACEMENT_RESTRICTION);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────────

    private static StudentProfile backlogProfile(int activeBacklogs) {
        StudentProfile p = validProfile();
        p.getAcademic().setActiveBacklogs(activeBacklogs);
        return p;
    }

    private static StudentProfile placedProfile() {
        StudentProfile p = validProfile();
        p.setPlaced(true);
        return p;
    }

    private static EligibilityContext contextWithProfile(StudentProfile p) {
        return new EligibilityContext(AccountStatus.ACTIVE, p, validDrive(), validPolicy(), false, NOW);
    }

    private static EligibilityContext contextWithDrive(Drive d) {
        return new EligibilityContext(AccountStatus.ACTIVE, validProfile(), d, validPolicy(), false, NOW);
    }

    private static void assertFailedRule(EligibilityContext ctx, RuleId expected) {
        EligibilityResult result = EligibilityEngine.check(ctx);
        assertThat(result.eligible()).isFalse();
        assertThat(result.failedRule()).isEqualTo(expected);
        assertThat(result.reason()).isNotBlank();
    }
}
