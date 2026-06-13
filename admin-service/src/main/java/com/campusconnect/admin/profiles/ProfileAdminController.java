package com.campusconnect.admin.profiles;

import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * College-Admin student-profile approval endpoints (Story 3.3, FR-9). Authenticated and restricted to
 * COLLEGE_ADMIN; every operation is tenant-scoped in the service. Unlike the student's own-profile path,
 * the admin acts on any profile in their tenant via the {studentId} path variable.
 */
@RestController
@RequestMapping("/api/admin/profiles")
@PreAuthorize("hasRole('COLLEGE_ADMIN')")
public class ProfileAdminController {

    private final ProfileApprovalService approvalService;

    public ProfileAdminController(ProfileApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ApiResponse<List<PendingProfileResponse>> list(
            @RequestParam(value = "status", defaultValue = "PENDING_APPROVAL") ProfileApprovalStatus status) {
        return ApiResponse.ok(approvalService.listByStatus(status));
    }

    @PostMapping("/{studentId}/approve")
    public ApiResponse<Boolean> approve(@PathVariable String studentId) {
        approvalService.approve(studentId);
        return ApiResponse.ok(Boolean.TRUE, "Profile approved.");
    }

    @PostMapping("/{studentId}/reject")
    public ApiResponse<Boolean> reject(@PathVariable String studentId,
                                       @Valid @RequestBody RejectProfileRequest request) {
        approvalService.reject(studentId, request.reason());
        return ApiResponse.ok(Boolean.TRUE, "Profile rejected.");
    }

    @PatchMapping("/{studentId}")
    public ApiResponse<Boolean> edit(@PathVariable String studentId,
                                     @Valid @RequestBody AdminEditProfileRequest request) {
        approvalService.editAcademics(studentId, request);
        return ApiResponse.ok(Boolean.TRUE, "Profile updated.");
    }
}
