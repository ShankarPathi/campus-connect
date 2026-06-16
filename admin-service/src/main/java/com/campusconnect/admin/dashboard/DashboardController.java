package com.campusconnect.admin.dashboard;

import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The College-Admin / TPO dashboard (Story 8.4, FR-27). Base path {@code /api/admin/dashboard} requires a
 * valid token (shared chain); {@code @PreAuthorize} narrows it to COLLEGE_ADMIN (active-status enforced by
 * the Story 2.5 filter). Thin — it only adapts HTTP to {@link DashboardService}, which tenant-scopes every
 * figure. Read-only.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('COLLEGE_ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** My tenant's season snapshot — pending approvals + season counts. */
    @GetMapping
    public ApiResponse<DashboardSnapshotResponse> snapshot() {
        return ApiResponse.ok(dashboardService.snapshot());
    }
}
