package com.campusconnect.student.platform;

import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;

import java.time.LocalDate;
import java.util.List;

/** The provisioned tenant, returned to the platform admin. Carries only the tenant's own fields. */
public record TenantResponse(
        String id,
        String name,
        String slug,
        String subdomain,
        List<String> branches,
        List<String> batches,
        LocalDate seasonStart,
        LocalDate seasonEnd,
        TenantStatus status) {

    public static TenantResponse from(Tenant t) {
        return new TenantResponse(
                t.getId(),
                t.getName(),
                t.getSlug(),
                t.getSubdomain(),
                t.getBranches(),
                t.getBatches(),
                t.getSeason() != null ? t.getSeason().getStart() : null,
                t.getSeason() != null ? t.getSeason().getEnd() : null,
                t.getStatus());
    }
}
