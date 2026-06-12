package com.campusconnect.admin.recruiters;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * College-Admin recruiter approval endpoints (Story 2.2). These are not public auth paths, so they
 * require authentication; {@code @PreAuthorize} restricts them to COLLEGE_ADMIN. Every operation is
 * tenant-scoped in the service via {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/admin/recruiters")
@PreAuthorize("hasRole('COLLEGE_ADMIN')")
public class RecruiterAdminController {

    private final RecruiterApprovalService approvalService;

    public RecruiterAdminController(RecruiterApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ApiResponse<List<PendingRecruiterResponse>> list(
            @RequestParam(value = "status", defaultValue = "PENDING_APPROVAL") AccountStatus status) {
        return ApiResponse.ok(approvalService.listByStatus(status));
    }

    @PostMapping("/{userId}/approve")
    public ApiResponse<Boolean> approve(@PathVariable String userId) {
        approvalService.approve(userId);
        return ApiResponse.ok(Boolean.TRUE, "Recruiter approved.");
    }

    @PostMapping("/{userId}/reject")
    public ApiResponse<Boolean> reject(
            @PathVariable String userId, @Valid @RequestBody RejectRecruiterRequest request) {
        approvalService.reject(userId, request.reason());
        return ApiResponse.ok(Boolean.TRUE, "Recruiter rejected.");
    }
}
