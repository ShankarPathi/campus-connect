package com.campusconnect.recruiter.rounds;

import com.campusconnect.common.domain.InterviewMode;

import java.time.Instant;
import java.util.List;

/**
 * The drive's interview-round sequence with per-round assigned counts (Story 6.3, FR-20). Returned by the
 * define and reschedule writes and the {@code GET /rounds} read. {@code assignedCount} is the number of
 * {@code applicationRounds} rows for that round.
 */
public record RoundsResponse(List<RoundResponse> rounds) {

    public record RoundResponse(
            int roundOrder,
            String name,
            InterviewMode mode,
            Instant schedule,
            String venueOrLink,
            long assignedCount) {
    }
}
