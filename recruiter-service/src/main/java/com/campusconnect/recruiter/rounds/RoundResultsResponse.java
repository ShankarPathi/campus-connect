package com.campusconnect.recruiter.rounds;

import java.util.List;

/**
 * The result of recording per-student round results (Story 6.4, Decision A — the 6.2 resilient-bulk shape).
 * Always returned with HTTP 200: each student is independent, so a missing/already-decided/illegal item is
 * reported in {@code failed} with a reason while the rest are recorded. A single-entry call gets a one-entry
 * summary. (Mirrors {@code BulkDecisionResponse}; kept in the {@code rounds} package to avoid coupling.)
 */
public record RoundResultsResponse(
        List<String> succeeded,
        List<FailedItem> failed,
        int succeededCount,
        int failedCount) {

    /** One student whose result could not be recorded, with the reason. */
    public record FailedItem(String applicationId, String reason) {
    }

    public static RoundResultsResponse of(List<String> succeeded, List<FailedItem> failed) {
        return new RoundResultsResponse(succeeded, failed, succeeded.size(), failed.size());
    }
}
