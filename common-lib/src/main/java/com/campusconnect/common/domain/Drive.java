package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A placement drive posted by a recruiter (Story 4.1, FR-10). Tenant-scoped and owned by exactly one
 * recruiter: {@code createdBy} is the authenticated recruiter's {@link User} id. A recruiter sees and
 * edits only their own drives (the 2.5 "recruiter → own drives" convention) — cross-owner and
 * cross-tenant reads both resolve to not-found via {@code DriveRepository}.
 *
 * <p>Drafted/edited here (4.1, {@code DRAFT} only); submitted (4.2 → {@code PENDING_APPROVAL}); admin
 * approves/edits/rejects → {@code PUBLISHED} (4.3); lifecycle/cancellation (4.4). The embedded
 * {@link EligibilityCriteria} is what the Epic-5 engine matches against a profile. {@code companyName}
 * is snapshotted from the recruiter's {@link RecruiterProfile} at creation — a normalized
 * {@code Company} entity is deliberately deferred (lands when a shared company surface needs it).
 *
 * <p>Note: the CTC field is {@code packageLpa} (lakhs-per-annum), not {@code package} — {@code package}
 * is a Java keyword. It is numeric so the Epic-5/7 placement-restriction rule can compare it.
 */
@Document("drives")
@CompoundIndex(name = "idx_tenant_created_by", def = "{'tenantId': 1, 'createdBy': 1}")
public class Drive extends TenantAwareDocument {

    private String createdBy;
    private String companyName;
    private String role;
    private Double packageLpa;
    private String location;
    private EligibilityCriteria eligibility = new EligibilityCriteria();
    private Integer openings;
    private Instant applyDeadline;
    private DriveStatus status = DriveStatus.DRAFT;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Double getPackageLpa() {
        return packageLpa;
    }

    public void setPackageLpa(Double packageLpa) {
        this.packageLpa = packageLpa;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public EligibilityCriteria getEligibility() {
        return eligibility;
    }

    public void setEligibility(EligibilityCriteria eligibility) {
        this.eligibility = eligibility;
    }

    public Integer getOpenings() {
        return openings;
    }

    public void setOpenings(Integer openings) {
        this.openings = openings;
    }

    public Instant getApplyDeadline() {
        return applyDeadline;
    }

    public void setApplyDeadline(Instant applyDeadline) {
        this.applyDeadline = applyDeadline;
    }

    public DriveStatus getStatus() {
        return status;
    }

    public void setStatus(DriveStatus status) {
        this.status = status;
    }
}
