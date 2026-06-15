package com.campusconnect.recruiter.rounds;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * The body to reschedule a single not-yet-occurred interview round (Story 6.3, FR-20). The new
 * {@code schedule} must be in the future; {@code venueOrLink} is optional (null/blank leaves it unchanged).
 */
public record RescheduleRoundRequest(
        @NotNull @Future Instant schedule,
        String venueOrLink) {
}
