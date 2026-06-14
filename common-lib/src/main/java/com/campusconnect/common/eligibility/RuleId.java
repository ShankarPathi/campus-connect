package com.campusconnect.common.eligibility;

/**
 * The ten eligibility rules (FR-12), <b>in evaluation order</b>. {@link EligibilityEngine#check} runs
 * them top-to-bottom and short-circuits on the first failure, so the order here is load-bearing: the
 * early rules (account/profile/drive/deadline/duplicate) are the cheap "is this even applyable" gates;
 * the later rules (batch/branch/cgpa/backlog/placement) are the eligibility match. An
 * {@link EligibilityResult#failedRule()} is exactly one of these.
 */
public enum RuleId {
    ACCOUNT_ACTIVE,
    PROFILE_APPROVED,
    DRIVE_OPEN,
    WITHIN_DEADLINE,
    NO_DUPLICATE_APPLICATION,
    BATCH_MATCH,
    BRANCH_ELIGIBLE,
    CGPA_MET,
    BACKLOG_POLICY,
    PLACEMENT_RESTRICTION
}
