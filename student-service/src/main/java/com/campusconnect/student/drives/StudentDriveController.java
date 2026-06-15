package com.campusconnect.student.drives;

import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The authenticated student's pre-apply drive list (Story 5.3, FR-13). Base path
 * {@code /api/student/drives} requires a valid token (shared chain); {@code @PreAuthorize} narrows it to
 * the STUDENT role (active-status enforced by the Story 2.5 filter). Every drive is returned tagged with
 * its eligibility group — Eligible / Applied / Not Eligible / Closed — and not-eligible drives carry the
 * specific failed criteria.
 */
@RestController
@RequestMapping("/api/student/drives")
@PreAuthorize("hasRole('STUDENT')")
public class StudentDriveController {

    private final StudentDriveService driveService;

    public StudentDriveController(StudentDriveService driveService) {
        this.driveService = driveService;
    }

    @GetMapping
    public ApiResponse<List<StudentDriveResponse>> listDrives() {
        return ApiResponse.ok(driveService.listDrives());
    }
}
