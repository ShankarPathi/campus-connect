package com.campusconnect.admin.eligibility;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.PlacementPolicy;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.eligibility.PolicyResolver;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * College-Admin read/replace of the tenant eligibility policy (Story 5.2, FR-14). The policy lives on
 * the {@code tenants} document — the isolation root, NOT a {@code TenantAwareDocument} — so there is no
 * tenant-aware repository magic: every operation loads exactly the caller's own tenant by its id (the
 * authenticated {@code tenantId} claim), making cross-tenant reach structurally impossible. A replace is
 * written to the append-only audit trail. The change persists to the document and takes effect on the
 * next {@link PolicyResolver#resolve} — no redeployment (FR-14).
 */
@Service
public class EligibilityPolicyService {

    private static final String ENTITY_TYPE = "Tenant";

    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    public EligibilityPolicyService(TenantRepository tenantRepository, AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
    }

    /** The caller's effective tenant policy (tenant values coalesced with the platform default). */
    public EligibilityPolicyResponse get() {
        Tenant tenant = loadOwnTenant();
        return EligibilityPolicyResponse.from(PolicyResolver.effectiveTenantPolicy(tenant.getPlacementPolicy()));
    }

    /** Replace the caller's tenant policy with the request's values (null = inherit default); audited. */
    public EligibilityPolicyResponse update(UpdateEligibilityPolicyRequest request) {
        Tenant tenant = loadOwnTenant();
        PlacementPolicy previous = tenant.getPlacementPolicy();
        PlacementPolicy updated = new PlacementPolicy(
                request.minCgpaFloor(), request.placedStudentsMayApply(), request.reapplyPackageThresholdLpa());

        tenant.setPlacementPolicy(updated);
        tenantRepository.save(tenant);
        auditService.record(AuditAction.POLICY_EDITED, ENTITY_TYPE, tenant.getId(),
                describe(previous), describe(updated));

        return EligibilityPolicyResponse.from(PolicyResolver.effectiveTenantPolicy(updated));
    }

    private Tenant loadOwnTenant() {
        return tenantRepository.findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found."));
    }

    private static String describe(PlacementPolicy p) {
        if (p == null) {
            return "none";
        }
        return "minCgpaFloor=" + p.getMinCgpaFloor()
                + ", placedStudentsMayApply=" + p.getPlacedStudentsMayApply()
                + ", reapplyPackageThresholdLpa=" + p.getReapplyPackageThresholdLpa();
    }
}
