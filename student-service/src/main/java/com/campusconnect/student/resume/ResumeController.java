package com.campusconnect.student.resume;

import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The authenticated student's own resume (Story 3.2, FR-8). Base path {@code /api/student/resume} is not
 * a public auth path, so the shared chain already requires a token; {@code @PreAuthorize} narrows it to
 * the STUDENT role (active-status enforced by the Story 2.5 filter). No resume-id parameter — the
 * principal IS the owner.
 */
@RestController
@RequestMapping("/api/student/resume")
@PreAuthorize("hasRole('STUDENT')")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<ResumeResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(resumeService.upload(file), "Resume uploaded.");
    }

    @GetMapping
    public ApiResponse<ResumeResponse> getMyResume() {
        return ApiResponse.ok(resumeService.getMyResume());
    }
}
