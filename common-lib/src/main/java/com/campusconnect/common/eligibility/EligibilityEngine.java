package com.campusconnect.common.eligibility;

import com.campusconnect.common.domain.AcademicDetails;
import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.campusconnect.common.domain.AccountStatus.ACTIVE;

/**
 * The pure, side-effect-free eligibility engine (Story 5.1, FR-12) — the crown jewel of Epic 5. It
 * evaluates the ten ordered rules (see {@link RuleId}) against an {@link EligibilityContext} and returns
 * the <b>first</b> failing rule with a human-readable reason, or {@link EligibilityResult#pass()}.
 *
 * <p><b>Pure and deterministic:</b> no Spring, no repository, no I/O, and no clock — {@code now} is an
 * input. The same context always yields the same result. The ten rules live in <b>one ordered list</b>
 * ({@link #RULES}) so the 5.3 {@code checkAll} transparency variant and the 5.4 apply gate reuse the
 * identical predicates — there is never a second definition of "eligible".
 *
 * <p><b>Total and null-safe:</b> a missing student field (cgpa, branch, batch, active-backlogs) fails
 * its own rule with a clear reason rather than throwing. The engine reads defensively.
 */
public final class EligibilityEngine {

    /** A single rule: its id and a predicate returning {@code Optional.empty()} on pass, or the failure reason. */
    private record Rule(RuleId id, Function<EligibilityContext, Optional<String>> evaluate) {
    }

    /** The ten rules, in evaluation order — the single source of truth for "eligible". */
    private static final List<Rule> RULES = List.of(
            new Rule(RuleId.ACCOUNT_ACTIVE, EligibilityEngine::accountActive),
            new Rule(RuleId.PROFILE_APPROVED, EligibilityEngine::profileApproved),
            new Rule(RuleId.DRIVE_OPEN, EligibilityEngine::driveOpen),
            new Rule(RuleId.WITHIN_DEADLINE, EligibilityEngine::withinDeadline),
            new Rule(RuleId.NO_DUPLICATE_APPLICATION, EligibilityEngine::noDuplicate),
            new Rule(RuleId.BATCH_MATCH, EligibilityEngine::batchMatch),
            new Rule(RuleId.BRANCH_ELIGIBLE, EligibilityEngine::branchEligible),
            new Rule(RuleId.CGPA_MET, EligibilityEngine::cgpaMet),
            new Rule(RuleId.BACKLOG_POLICY, EligibilityEngine::backlogPolicy),
            new Rule(RuleId.PLACEMENT_RESTRICTION, EligibilityEngine::placementRestriction));

    private EligibilityEngine() {
    }

    /**
     * Evaluate the ten ordered rules, short-circuiting on the first failure.
     *
     * @return {@link EligibilityResult#pass()} if every rule passes, else {@code fail(ruleId, reason)}
     *         for the first failing rule.
     */
    public static EligibilityResult check(EligibilityContext ctx) {
        for (Rule rule : RULES) {
            Optional<String> failure = rule.evaluate().apply(ctx);
            if (failure.isPresent()) {
                return EligibilityResult.fail(rule.id(), failure.get());
            }
        }
        return EligibilityResult.pass();
    }

    /**
     * Evaluate <b>all</b> ten rules (no short-circuit) and return every rule's verdict — the
     * transparency variant (Story 5.3, FR-13) powering the pre-apply eligibility panel. Uses the
     * <b>same</b> ordered {@link #RULES} list as {@link #check}, so there is never a second definition
     * of "eligible": {@code check} stops at the first failure (the apply gate), {@code checkAll} reports
     * each rule's pass/fail for display.
     *
     * @return an {@link EligibilityReport} whose {@code outcomes} are in {@link RuleId} order and whose
     *         {@code eligible} is true only when every rule passed.
     */
    public static EligibilityReport checkAll(EligibilityContext ctx) {
        List<RuleOutcome> outcomes = new ArrayList<>(RULES.size());
        boolean eligible = true;
        for (Rule rule : RULES) {
            Optional<String> failure = rule.evaluate().apply(ctx);
            outcomes.add(new RuleOutcome(rule.id(), failure.isEmpty(), failure.orElse(null)));
            if (failure.isPresent()) {
                eligible = false;
            }
        }
        return new EligibilityReport(eligible, List.copyOf(outcomes));
    }

    // ── The ten rules ──────────────────────────────────────────────────────────────────────────────

    private static Optional<String> accountActive(EligibilityContext ctx) {
        return ctx.accountStatus() == ACTIVE
                ? Optional.empty()
                : Optional.of("Your account is not active.");
    }

    private static Optional<String> profileApproved(EligibilityContext ctx) {
        StudentProfile p = ctx.profile();
        return p != null && p.getProfileApprovalStatus() == ProfileApprovalStatus.APPROVED
                ? Optional.empty()
                : Optional.of("Your profile is not approved yet.");
    }

    private static Optional<String> driveOpen(EligibilityContext ctx) {
        DriveStatus status = ctx.drive() == null ? null : ctx.drive().getStatus();
        return status == DriveStatus.PUBLISHED || status == DriveStatus.ONGOING
                ? Optional.empty()
                : Optional.of("This drive is not open for applications.");
    }

    private static Optional<String> withinDeadline(EligibilityContext ctx) {
        Instant deadline = ctx.drive() == null ? null : ctx.drive().getApplyDeadline();
        return deadline != null && ctx.now() != null && !ctx.now().isAfter(deadline)
                ? Optional.empty()
                : Optional.of("The application deadline has passed.");
    }

    private static Optional<String> noDuplicate(EligibilityContext ctx) {
        return ctx.alreadyApplied()
                ? Optional.of("You have already applied to this drive.")
                : Optional.empty();
    }

    private static Optional<String> batchMatch(EligibilityContext ctx) {
        String required = criteria(ctx).getBatch();
        if (required == null || required.isBlank()) {
            return Optional.empty();
        }
        String studentBatch = ctx.profile() == null ? null : ctx.profile().getBatch();
        return required.equals(studentBatch)
                ? Optional.empty()
                : Optional.of("This drive is open to the " + required + " batch.");
    }

    private static Optional<String> branchEligible(EligibilityContext ctx) {
        List<String> branches = criteria(ctx).getBranches();
        if (branches == null || branches.isEmpty()) {
            return Optional.empty();
        }
        String studentBranch = academic(ctx).getBranch();
        return studentBranch != null && branches.contains(studentBranch)
                ? Optional.empty()
                : Optional.of("Your branch (" + display(studentBranch) + ") is not eligible for this drive.");
    }

    private static Optional<String> cgpaMet(EligibilityContext ctx) {
        Double floor = ctx.resolvedPolicy() == null ? null : ctx.resolvedPolicy().minCgpa();
        if (floor == null) {
            return Optional.empty();
        }
        Double cgpa = academic(ctx).getCgpa();
        return cgpa != null && cgpa >= floor
                ? Optional.empty()
                : Optional.of("Your CGPA (" + display(cgpa) + ") is below the required " + floor + ".");
    }

    private static Optional<String> backlogPolicy(EligibilityContext ctx) {
        BacklogPolicy policy = ctx.resolvedPolicy() == null ? null : ctx.resolvedPolicy().backlogPolicy();
        if (policy != BacklogPolicy.NO_BACKLOG) {
            return Optional.empty();
        }
        Integer active = academic(ctx).getActiveBacklogs();
        return active != null && active == 0
                ? Optional.empty()
                : Optional.of("This drive does not allow active backlogs.");
    }

    private static Optional<String> placementRestriction(EligibilityContext ctx) {
        StudentProfile p = ctx.profile();
        if (p == null || !p.isPlaced()) {
            return Optional.empty();
        }
        ResolvedPolicy policy = ctx.resolvedPolicy();
        if (policy == null || !policy.placedStudentsMayApply()) {
            return Optional.of("Placed students may not apply to this drive.");
        }
        Double threshold = policy.reapplyPackageThresholdLpa();
        Double pkg = ctx.drive() == null ? null : ctx.drive().getPackageLpa();
        if (threshold != null && (pkg == null || pkg < threshold)) {
            return Optional.of("As a placed student, you may only apply to drives offering "
                    + threshold + " LPA or above.");
        }
        return Optional.empty();
    }

    // ── Defensive readers ──────────────────────────────────────────────────────────────────────────

    private static EligibilityCriteria criteria(EligibilityContext ctx) {
        EligibilityCriteria c = ctx.drive() == null ? null : ctx.drive().getEligibility();
        return c != null ? c : new EligibilityCriteria();
    }

    private static AcademicDetails academic(EligibilityContext ctx) {
        AcademicDetails a = ctx.profile() == null ? null : ctx.profile().getAcademic();
        return a != null ? a : new AcademicDetails();
    }

    private static String display(Object value) {
        return value == null ? "not set" : value.toString();
    }
}
