package com.campusconnect.student.applications;

import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated student's apply action (Story 5.4, FR-15). {@code POST /api/student/drives/{driveId}/apply}
 * requires a valid token (shared chain); {@code @PreAuthorize} narrows it to the STUDENT role
 * (active-status enforced by the Story 2.5 filter). The student is always the application owner — there is
 * no student id in the path or body.
 */
@RestController
@PreAuthorize("hasRole('STUDENT')")
public class ApplyController {

    private final ApplyService applyService;

    public ApplyController(ApplyService applyService) {
        this.applyService = applyService;
    }

    @PostMapping("/api/student/drives/{driveId}/apply")
    public ApiResponse<ApplicationResponse> apply(@PathVariable String driveId) {
        return ApiResponse.ok(applyService.apply(driveId), "Application submitted.");
    }
}
