package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * A college tenant. The {@code tenants} collection is the isolation root — it is NOT itself
 * tenant-scoped (it does not extend {@link TenantAwareDocument}). Managed by the platform admin via
 * the non-tenant-aware {@code TenantRepository}.
 *
 * <p>{@code branches} and {@code batches} are embedded (small, read together). {@code placementPolicy}
 * is the typed tenant-level eligibility policy (Story 5.2, FR-14) the {@code PolicyResolver} merges with
 * each drive's criteria; a fresh tenant's policy is all-null (inherits every platform default).
 */
@Document("tenants")
public class Tenant extends BaseDocument {

    @Indexed(unique = true)
    private String slug;

    private String name;
    private String subdomain;
    private List<String> branches = new ArrayList<>();
    private List<String> batches = new ArrayList<>();
    private Season season;
    private PlacementPolicy placementPolicy = new PlacementPolicy();
    private String plan;
    private TenantStatus status = TenantStatus.ACTIVE;

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public List<String> getBranches() {
        return branches;
    }

    public void setBranches(List<String> branches) {
        this.branches = branches;
    }

    public List<String> getBatches() {
        return batches;
    }

    public void setBatches(List<String> batches) {
        this.batches = batches;
    }

    public Season getSeason() {
        return season;
    }

    public void setSeason(Season season) {
        this.season = season;
    }

    public PlacementPolicy getPlacementPolicy() {
        return placementPolicy;
    }

    public void setPlacementPolicy(PlacementPolicy placementPolicy) {
        this.placementPolicy = placementPolicy;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }
}
