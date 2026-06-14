package com.campusconnect.common.eligibility;

/**
 * The outcome of an eligibility evaluation (Story 5.1, FR-12): either a {@link #pass()} or the
 * <b>first</b> failing rule with a human-readable {@code reason}. Ineligibility is a <i>result</i>, not
 * an exception — the engine throws nothing for an ineligible student; the apply path (5.4) maps a
 * failed result to {@code NOT_ELIGIBLE} (403) carrying {@code reason}, and the 5.3 transparency panel
 * shows the reason directly.
 *
 * @param eligible   true when every rule passed
 * @param failedRule the rule that failed (null on pass)
 * @param reason     a specific, student-facing explanation of the failure (null on pass)
 */
public record EligibilityResult(boolean eligible, RuleId failedRule, String reason) {

    private static final EligibilityResult PASS = new EligibilityResult(true, null, null);

    /** The student satisfies all ten rules. */
    public static EligibilityResult pass() {
        return PASS;
    }

    /** The student failed {@code rule}; {@code reason} explains why (shown to the student). */
    public static EligibilityResult fail(RuleId rule, String reason) {
        return new EligibilityResult(false, rule, reason);
    }
}
