package com.campusconnect.recruiter.applications;

import java.util.List;

/**
 * The result of marking final selections (Story 6.5, FR-22 — the 6.2 resilient-bulk shape, plus the openings
 * signal). Always returned with HTTP 200: each applicant is independent, so a not-passed / unknown / illegal
 * item is reported in {@code failed} with a reason while the rest are selected. {@code selectedTotal} is the
 * drive's total {@code SELECTED} count after the batch; {@code warning} is non-null when that total has
 * reached the drive's {@code openings} — a soft signal, never a block.
 */
public record SelectionResponse(
        List<String> succeeded,
        List<FailedItem> failed,
        int succeededCount,
        int failedCount,
        int selectedTotal,
        Integer openings,
        String warning) {

    /** One applicant who could not be selected, with the reason (not found / not passed final round / illegal / conflict). */
    public record FailedItem(String applicationId, String reason) {
    }

    public static SelectionResponse of(List<String> succeeded, List<FailedItem> failed,
                                       int selectedTotal, Integer openings) {
        String warning = (openings != null && selectedTotal >= openings)
                ? "Selections (" + selectedTotal + ") have reached the drive's openings (" + openings + ")."
                : null;
        return new SelectionResponse(succeeded, failed, succeeded.size(), failed.size(),
                selectedTotal, openings, warning);
    }
}
