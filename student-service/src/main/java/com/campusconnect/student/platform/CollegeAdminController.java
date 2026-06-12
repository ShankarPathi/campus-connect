package com.campusconnect.student.platform;

import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-admin bootstrap of a tenant's College Admin (FR-2). Hosted in student-service (architecture §3). */
@RestController
@RequestMapping("/api/platform/tenants/{tenantId}/admins")
public class CollegeAdminController {

    private final CollegeAdminBootstrapService bootstrapService;

    public CollegeAdminController(CollegeAdminBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<CollegeAdminResponse>> create(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateCollegeAdminRequest request) {
        CollegeAdminResponse created = bootstrapService.bootstrap(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }
}
