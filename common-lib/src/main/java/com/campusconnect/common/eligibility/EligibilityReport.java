package com.campusconnect.common.eligibility;

import java.util.List;

/**
 * The full per-rule verdict of {@link EligibilityEngine#checkAll} (Story 5.3, FR-13) — every rule's
 * pass/fail, for the pre-apply transparency panel. Contrast {@link EligibilityResult} (the short-circuit
 * {@code check} result that drives the apply gate).
 *
 * @param eligible true only when every rule passed
 * @param outcomes each rule's verdict, in {@link RuleId} order
 */
public record EligibilityReport(boolean eligible, List<RuleOutcome> outcomes) {

    /** The rules the student does NOT satisfy — the criteria shown on a Not-Eligible drive. */
    public List<RuleOutcome> failures() {
        return outcomes.stream().filter(o -> !o.passed()).toList();
    }
}
