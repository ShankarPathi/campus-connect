package com.campusconnect.admin.placements;

import com.campusconnect.common.domain.PlacementStatus;
import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * College-Admin placement-confirmation endpoints (Story 7.4, FR-25). Authenticated and restricted to
 * COLLEGE_ADMIN; every operation is tenant-scoped in the service (a placement in another tenant is 404).
 * The confirmation queue ({@code GET}, default {@code PENDING_CONFIRMATION}) and the single official
 * confirmation ({@code POST .../confirm}) that makes a placement count in reports. Closes Epic 7.
 */
@RestController
@RequestMapping("/api/admin/placements")
@PreAuthorize("hasRole('COLLEGE_ADMIN')")
public class PlacementAdminController {

    private final PlacementConfirmationService confirmationService;

    public PlacementAdminController(PlacementConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    /** The tenant's placement records (default the PENDING_CONFIRMATION confirmation queue). */
    @GetMapping
    public ApiResponse<List<PlacementResponse>> list(
            @RequestParam(value = "status", defaultValue = "PENDING_CONFIRMATION") PlacementStatus status) {
        return ApiResponse.ok(confirmationService.listByStatus(status));
    }

    /** Officially confirm one pending placement → OFFICIALLY_PLACED (audited). */
    @PostMapping("/{placementId}/confirm")
    public ApiResponse<PlacementResponse> confirm(@PathVariable String placementId) {
        return ApiResponse.ok(confirmationService.confirm(placementId), "Placement confirmed.");
    }
}
