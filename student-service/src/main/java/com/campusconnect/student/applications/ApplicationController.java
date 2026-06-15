package com.campusconnect.student.applications;

import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated student's applications (Story 5.5, FR-16). Base path {@code /api/student/applications}
 * requires a valid token (shared chain); {@code @PreAuthorize} narrows it to the STUDENT role
 * (active-status enforced by the Story 2.5 filter). Every operation is owner-scoped in the service — the
 * student acts only on their own applications. (Story 5.6 adds the {@code GET} list here.)
 */
@RestController
@RequestMapping("/api/student/applications")
@PreAuthorize("hasRole('STUDENT')")
public class ApplicationController {

    private final WithdrawService withdrawService;

    public ApplicationController(WithdrawService withdrawService) {
        this.withdrawService = withdrawService;
    }

    @PostMapping("/{applicationId}/withdraw")
    public ApiResponse<ApplicationResponse> withdraw(@PathVariable String applicationId) {
        return ApiResponse.ok(withdrawService.withdraw(applicationId), "Application withdrawn.");
    }
}
