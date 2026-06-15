package com.campusconnect.recruiter.applications;

import java.util.List;

/**
 * The result of a per-item shortlist/reject (Story 6.2, Decision C). Always returned with HTTP 200 — each
 * applicant is independent, so a stale/illegal item is reported in {@code failed} with a reason while the
 * rest still apply. A single-item call simply gets a one-entry summary.
 */
public record BulkDecisionResponse(
        List<String> succeeded,
        List<FailedItem> failed,
        int succeededCount,
        int failedCount) {

    /** One applicant that could not be transitioned, with the reason (not found / illegal state / conflict). */
    public record FailedItem(String applicationId, String reason) {
    }

    public static BulkDecisionResponse of(List<String> succeeded, List<FailedItem> failed) {
        return new BulkDecisionResponse(succeeded, failed, succeeded.size(), failed.size());
    }
}
