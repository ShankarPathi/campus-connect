package com.campusconnect.common.domain;

/**
 * Base for every <b>tenant-scoped</b> document. Carries the {@code tenantId} that the
 * {@code TenantAwareRepository} stamps on write and filters on read. Every business entity
 * (StudentProfile, Drive, Application, …) extends this in later stories.
 *
 * <p>The {@code tenants} collection itself and the global platform-admin users are the only
 * documents that do NOT extend this (they are not scoped to another tenant).
 */
public abstract class TenantAwareDocument extends BaseDocument {

    private String tenantId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
