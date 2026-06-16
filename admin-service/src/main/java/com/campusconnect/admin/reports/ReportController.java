package com.campusconnect.admin.reports;

import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * College-Admin placement reports (Story 8.5, FR-26). Base path {@code /api/admin/reports} requires a valid
 * token (shared chain); {@code @PreAuthorize} narrows it to COLLEGE_ADMIN (active-status enforced by the Story
 * 2.5 filter). Thin — it only adapts HTTP to {@link ReportService}, which tenant-scopes every figure/row. The
 * CSV export returns raw {@code text/csv} (a file download), NOT the {@code ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('COLLEGE_ADMIN')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Overall %, branch-wise, and company-wise placement breakdowns for my tenant. */
    @GetMapping("/placements")
    public ApiResponse<PlacementReportResponse> placements() {
        return ApiResponse.ok(reportService.report());
    }

    /** The detailed officially-placed list as a CSV file download. */
    @GetMapping("/placements/export")
    public ResponseEntity<String> exportPlacements() {
        String filename = "placements-" + TenantContext.requireTenantId() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reportService.exportCsv());
    }
}
