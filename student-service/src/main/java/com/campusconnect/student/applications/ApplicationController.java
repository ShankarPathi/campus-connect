package com.campusconnect.student.applications;

import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The authenticated student's applications (Stories 5.5–5.6, FR-16/17). Base path
 * {@code /api/student/applications} requires a valid token (shared chain); {@code @PreAuthorize} narrows
 * it to the STUDENT role (active-status enforced by the Story 2.5 filter). Every operation is owner-scoped
 * in the service — the student acts only on, and sees only, their own applications.
 */
@RestController
@RequestMapping("/api/student/applications")
@PreAuthorize("hasRole('STUDENT')")
public class ApplicationController {

    private final WithdrawService withdrawService;
    private final MyApplicationsService myApplicationsService;

    public ApplicationController(WithdrawService withdrawService, MyApplicationsService myApplicationsService) {
        this.withdrawService = withdrawService;
        this.myApplicationsService = myApplicationsService;
    }

    /** My Applications — all the student's own applications, status + drive context, newest first (Story 5.6). */
    @GetMapping
    public ApiResponse<List<ApplicationResponse>> listMyApplications() {
        return ApiResponse.ok(myApplicationsService.listMyApplications());
    }

    @PostMapping("/{applicationId}/withdraw")
    public ApiResponse<ApplicationResponse> withdraw(@PathVariable String applicationId) {
        return ApiResponse.ok(withdrawService.withdraw(applicationId), "Application withdrawn.");
    }
}
