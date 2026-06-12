package com.campusconnect.student.profile;

import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated student's own placement profile (Story 3.1, FR-7). Base path
 * {@code /api/student/profile} is NOT a public auth path, so the shared security chain already requires
 * a valid token; {@code @PreAuthorize} narrows it to the STUDENT role (active-status is enforced by the
 * Story 2.5 filter). There is deliberately no profile-id parameter — the principal IS the owner.
 */
@RestController
@RequestMapping("/api/student/profile")
@PreAuthorize("hasRole('STUDENT')")
public class StudentProfileController {

    private final StudentProfileService profileService;

    public StudentProfileController(StudentProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<StudentProfileResponse> getMyProfile() {
        return ApiResponse.ok(profileService.getMyProfile());
    }

    @PutMapping
    public ApiResponse<StudentProfileResponse> saveMyProfile(@Valid @RequestBody StudentProfileRequest request) {
        return ApiResponse.ok(profileService.saveMyProfile(request), "Profile saved.");
    }

    @PostMapping("/submit")
    public ApiResponse<StudentProfileResponse> submitMyProfile() {
        return ApiResponse.ok(profileService.submitMyProfile(), "Profile submitted for approval.");
    }
}
