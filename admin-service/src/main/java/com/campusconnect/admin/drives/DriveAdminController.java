package com.campusconnect.admin.drives;

import com.campusconnect.common.domain.DriveStatus;
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
 * College-Admin drive approval endpoints (Story 4.3, FR-11). Authenticated and restricted to
 * COLLEGE_ADMIN; every operation is tenant-scoped in the service. The admin acts on any drive in their
 * tenant via the {id} path variable (cross-tenant is a 404).
 */
@RestController
@RequestMapping("/api/admin/drives")
@PreAuthorize("hasRole('COLLEGE_ADMIN')")
public class DriveAdminController {

    private final DriveApprovalService approvalService;

    public DriveAdminController(DriveApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ApiResponse<List<PendingDriveResponse>> list(
            @RequestParam(value = "status", defaultValue = "PENDING_APPROVAL") DriveStatus status) {
        return ApiResponse.ok(approvalService.listByStatus(status));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Boolean> approve(@PathVariable String id) {
        approvalService.approve(id);
        return ApiResponse.ok(Boolean.TRUE, "Drive approved and published.");
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Boolean> reject(@PathVariable String id, @Valid @RequestBody RejectDriveRequest request) {
        approvalService.reject(id, request.reason());
        return ApiResponse.ok(Boolean.TRUE, "Drive rejected.");
    }

    @PatchMapping("/{id}")
    public ApiResponse<Boolean> edit(@PathVariable String id,
                                     @Valid @RequestBody AdminEditDriveCriteriaRequest request) {
        approvalService.editCriteria(id, request);
        return ApiResponse.ok(Boolean.TRUE, "Drive criteria updated.");
    }
}
