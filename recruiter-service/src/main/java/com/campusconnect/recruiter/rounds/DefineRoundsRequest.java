package com.campusconnect.recruiter.rounds;

import com.campusconnect.common.domain.InterviewMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * The body that defines a drive's ordered interview-round sequence (Story 6.3, FR-20). Round order is
 * positional — element <i>i</i> becomes {@code roundOrder = i+1}. Each round is validated; a violation →
 * 400 {@code VALIDATION_ERROR} with nothing written. {@code @Valid} cascades into each {@link RoundSpec}.
 */
public record DefineRoundsRequest(
        @NotEmpty @Size(max = 20) @Valid List<RoundSpec> rounds) {

    public record RoundSpec(
            @NotBlank String name,
            @NotNull InterviewMode mode,
            @NotNull @Future Instant schedule,
            @NotBlank String venueOrLink) {
    }
}
