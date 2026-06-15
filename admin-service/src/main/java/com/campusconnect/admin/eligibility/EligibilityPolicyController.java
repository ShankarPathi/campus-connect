package com.campusconnect.admin.eligibility;

import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * College-Admin eligibility-policy endpoints (Story 5.2, FR-14). Authenticated and restricted to
 * COLLEGE_ADMIN; both operations act on the caller's own tenant (resolved from the JWT claim in the
 * service), never a path-supplied tenant.
 */
@RestController
@RequestMapping("/api/admin/eligibility-policy")
@PreAuthorize("hasRole('COLLEGE_ADMIN')")
public class EligibilityPolicyController {

    private final EligibilityPolicyService service;

    public EligibilityPolicyController(EligibilityPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<EligibilityPolicyResponse> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<EligibilityPolicyResponse> update(@Valid @RequestBody UpdateEligibilityPolicyRequest request) {
        return ApiResponse.ok(service.update(request), "Eligibility policy updated.");
    }
}
