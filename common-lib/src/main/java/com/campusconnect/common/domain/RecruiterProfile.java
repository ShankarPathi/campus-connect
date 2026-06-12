package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The company details a recruiter submits at registration (Story 2.2). Tenant-scoped — a recruiter
 * belongs to the college they recruit at. The College Admin reviews these fields when approving or
 * rejecting the account. One profile per recruiter per tenant.
 *
 * <p>A normalized {@code Company} entity is deferred to Epic 4 (drives); for now the company fields
 * live here.
 */
@Document("recruiterProfiles")
@CompoundIndex(name = "uniq_tenant_user", def = "{'tenantId': 1, 'userId': 1}", unique = true)
public class RecruiterProfile extends TenantAwareDocument {

    private String userId;
    private String companyName;
    private String companyWebsite;
    private String industry;
    private String companyDescription;
    private String recruiterDesignation;
    private String contactPhone;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyWebsite() {
        return companyWebsite;
    }

    public void setCompanyWebsite(String companyWebsite) {
        this.companyWebsite = companyWebsite;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getCompanyDescription() {
        return companyDescription;
    }

    public void setCompanyDescription(String companyDescription) {
        this.companyDescription = companyDescription;
    }

    public String getRecruiterDesignation() {
        return recruiterDesignation;
    }

    public void setRecruiterDesignation(String recruiterDesignation) {
        this.recruiterDesignation = recruiterDesignation;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }
}
