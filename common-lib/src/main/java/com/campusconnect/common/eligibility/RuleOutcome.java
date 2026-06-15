package com.campusconnect.common.eligibility;

/**
 * One rule's verdict in an {@link EligibilityReport} (Story 5.3, FR-13). Produced by
 * {@link EligibilityEngine#checkAll} for the pre-apply transparency panel.
 *
 * @param id     the rule
 * @param passed whether the student satisfies it
 * @param reason the human-readable failure reason (null when {@code passed})
 */
public record RuleOutcome(RuleId id, boolean passed, String reason) {
}
