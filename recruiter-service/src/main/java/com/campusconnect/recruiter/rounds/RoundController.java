package com.campusconnect.recruiter.rounds;

import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recruiter interview-round endpoints (Story 6.3 / 6.4, FR-20 / FR-21). RECRUITER-only, nested under
 * {@code /drives/{driveId}} so the owner-scoped drive load in the service is the access gate (a drive that is
 * not the recruiter's own → 404). Define the sequence (+ enroll shortlisted into round 1), read it, reschedule
 * a not-yet-occurred round (6.3), and record per-student round results (6.4).
 */
@RestController
@RequestMapping("/api/recruiter/drives/{driveId}/rounds")
@PreAuthorize("hasRole('RECRUITER')")
public class RoundController {

    private final RoundService roundService;
    private final RoundResultService roundResultService;

    public RoundController(RoundService roundService, RoundResultService roundResultService) {
        this.roundService = roundService;
        this.roundResultService = roundResultService;
    }

    /** Define/replace the ordered round sequence and enroll all SHORTLISTED applicants into round 1 (idempotent). */
    @PutMapping
    public ApiResponse<RoundsResponse> define(@PathVariable String driveId,
                                              @Valid @RequestBody DefineRoundsRequest body) {
        return ApiResponse.ok(roundService.defineRounds(driveId, body), "Interview rounds defined.");
    }

    /** The drive's defined rounds with per-round assigned counts. */
    @GetMapping
    public ApiResponse<RoundsResponse> list(@PathVariable String driveId) {
        return ApiResponse.ok(roundService.getRounds(driveId));
    }

    /** Reschedule a not-yet-occurred round (schedule/venue); assigned students are notified (notify → Epic 8). */
    @PatchMapping("/{roundOrder}/reschedule")
    public ApiResponse<RoundsResponse> reschedule(@PathVariable String driveId,
                                                  @PathVariable int roundOrder,
                                                  @Valid @RequestBody RescheduleRoundRequest body) {
        return ApiResponse.ok(roundService.reschedule(driveId, roundOrder, body), "Round rescheduled.");
    }

    /** Record per-student results for a round (Story 6.4) — single or bulk; PASS advances, FAIL/ABSENT rejects. */
    @PostMapping("/{roundOrder}/results")
    public ApiResponse<RoundResultsResponse> recordResults(@PathVariable String driveId,
                                                           @PathVariable int roundOrder,
                                                           @Valid @RequestBody RecordResultsRequest body) {
        return ApiResponse.ok(roundResultService.recordResults(driveId, roundOrder, body.results()));
    }
}
