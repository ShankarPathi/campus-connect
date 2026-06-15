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
 * Pure unit tests for {@link EligibilityEngine#checkAll} (Story 5.3, FR-13) — the non-short-circuit
 * transparency variant. Same rule list as {@code check}, but every rule's verdict is reported.
 */
class EligibilityEngineCheckAllTest {

    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

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
        d.setApplyDeadline(NOW.plusSeconds(86_400));
        d.setPackageLpa(12.0);
        d.getEligibility().setBranches(new java.util.ArrayList<>(List.of("CSE", "ECE")));
        d.getEligibility().setBatch("2025");
        d.getEligibility().setMinCgpa(7.0);
        d.getEligibility().setBacklogPolicy(BacklogPolicy.NO_BACKLOG);
        return d;
    }

    private static ResolvedPolicy policy() {
        return new ResolvedPolicy(7.0, BacklogPolicy.NO_BACKLOG, false, null);
    }

    private static EligibilityContext context(StudentProfile p, Drive d) {
        return new EligibilityContext(AccountStatus.ACTIVE, p, d, policy(), false, NOW);
    }

    @Test
    void checkAll_allPass_tenOutcomesInRuleIdOrder_eligible() {
        EligibilityReport report = EligibilityEngine.checkAll(context(validProfile(), validDrive()));

        assertThat(report.eligible()).isTrue();
        assertThat(report.outcomes()).hasSize(RuleId.values().length);
        assertThat(report.outcomes()).extracting(RuleOutcome::id).containsExactly(RuleId.values());
        assertThat(report.outcomes()).allMatch(RuleOutcome::passed);
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void checkAll_doesNotShortCircuit_reportsEveryFailure() {
        // Fail rule 6 (batch) AND rule 8 (cgpa). check() would return only BATCH_MATCH;
        // checkAll must report BOTH as failures while every other rule still passes.
        StudentProfile p = validProfile();
        p.setBatch("2024");           // rule 6 fails
        p.getAcademic().setCgpa(6.4); // rule 8 fails

        EligibilityReport report = EligibilityEngine.checkAll(context(p, validDrive()));

        assertThat(report.eligible()).isFalse();
        assertThat(report.outcomes()).hasSize(10);
        assertThat(report.failures()).extracting(RuleOutcome::id)
                .containsExactly(RuleId.BATCH_MATCH, RuleId.CGPA_MET);
        // Each failure carries a specific, non-blank reason; passing rules carry none.
        assertThat(report.failures()).allSatisfy(o -> assertThat(o.reason()).isNotBlank());
        assertThat(report.outcomes()).filteredOn(RuleOutcome::passed).allSatisfy(o ->
                assertThat(o.reason()).isNull());
    }

    @Test
    void checkAll_cgpaFailure_reasonIsSpecific() {
        StudentProfile p = validProfile();
        p.getAcademic().setCgpa(6.4);

        EligibilityReport report = EligibilityEngine.checkAll(context(p, validDrive()));

        RuleOutcome cgpa = report.outcomes().stream()
                .filter(o -> o.id() == RuleId.CGPA_MET).findFirst().orElseThrow();
        assertThat(cgpa.passed()).isFalse();
        assertThat(cgpa.reason()).contains("6.4").contains("7.0");
    }
}
