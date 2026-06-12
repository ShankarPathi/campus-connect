package com.campusconnect.student.platform;

import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.exception.DuplicateResourceException;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.stereotype.Service;

/** Provisions new college tenants (FR-1). Platform-level — not tenant-scoped. */
@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;

    public TenantProvisioningService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantResponse provision(CreateTenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException(
                    ErrorCode.TENANT_SLUG_TAKEN, "Slug already in use: " + request.slug());
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setSlug(request.slug());
        tenant.setSubdomain(
                (request.subdomain() == null || request.subdomain().isBlank())
                        ? request.slug()
                        : request.subdomain());
        tenant.setBranches(request.branches());
        tenant.setBatches(request.batches());
        tenant.setSeason(new Season(request.seasonStart(), request.seasonEnd()));
        tenant.setStatus(TenantStatus.ACTIVE);

        return TenantResponse.from(tenantRepository.save(tenant));
    }
}
