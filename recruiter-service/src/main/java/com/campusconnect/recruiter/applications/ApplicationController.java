package com.campusconnect.recruiter.applications;

import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.web.ApiResponse;
import com.campusconnect.common.web.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recruiter applicant-review endpoints (Story 6.1, FR-18 / NFR-3). Authenticated and restricted to RECRUITER;
 * nested under {@code /drives/{driveId}} so the owner-scoped drive load in the service is the access gate — a
 * drive that is not the recruiter's own resolves to 404, so only applicants to the caller's own drives are
 * reachable. Read-only: no status transition (shortlist/reject is Story 6.2).
 */
@RestController
@RequestMapping("/api/recruiter/drives/{driveId}/applicants")
@PreAuthorize("hasRole('RECRUITER')")
public class ApplicationController {

    private final ApplicantReviewService applicantReviewService;

    public ApplicationController(ApplicantReviewService applicantReviewService) {
        this.applicantReviewService = applicantReviewService;
    }

    /** Filter ({@code status}), search ({@code search} — name/roll), sort ({@code sortBy}/{@code sortDir}), page. */
    @GetMapping
    public ApiResponse<PageResponse<ApplicantSummaryResponse>> list(
            @PathVariable String driveId,
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        ApplicantQuery query = new ApplicantQuery(status, search, sortBy, sortDir, page, pageSize);
        return ApiResponse.ok(applicantReviewService.listApplicants(driveId, query));
    }

    /** A fresh 15-minute pre-signed URL for the applicant's frozen résumé snapshot (owner + drive scoped). */
    @GetMapping("/{applicationId}/resume")
    public ApiResponse<ResumeUrlResponse> resume(@PathVariable String driveId,
                                                 @PathVariable String applicationId) {
        return ApiResponse.ok(applicantReviewService.resumeUrl(driveId, applicationId));
    }
}
