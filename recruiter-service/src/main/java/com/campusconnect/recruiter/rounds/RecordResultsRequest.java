package com.campusconnect.recruiter.rounds;

import com.campusconnect.common.domain.RoundResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body that records per-student results for one interview round (Story 6.4, FR-21). Same shape serves
 * single and bulk — a one-element list is "single". Each entry's {@code result} must be a real outcome
 * ({@code PASS}/{@code FAIL}/{@code ABSENT}); a {@code PENDING} request value is rejected per-item in the
 * service. A malformed request (empty/oversized list, blank id, null result) → 400 {@code VALIDATION_ERROR}.
 */
public record RecordResultsRequest(
        @NotEmpty @Size(max = 200) @Valid List<ResultEntry> results) {

    public record ResultEntry(
            @NotBlank String applicationId,
            @NotNull RoundResult result) {
    }
}
