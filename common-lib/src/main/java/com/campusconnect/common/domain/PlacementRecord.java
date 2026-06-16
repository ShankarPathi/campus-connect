package com.campusconnect.common.domain;

import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A student's placement record (architecture §5 {@code placementRecords}; Story 7.3, FR-24) — created when a
 * student <b>accepts</b> an offer, and the unit the admin officially confirms (Story 7.4) and reports count.
 * <b>Separate from the application by design</b>: it is a denormalized snapshot of the placement terms
 * ({@code company}, {@code ctc}, {@code role}, {@code joiningDate}) so reports never have to re-join the
 * offer/drive, and so the admin's official-placement workflow is independent of the application lifecycle.
 *
 * <p>Created in {@link PlacementStatus#PENDING_CONFIRMATION}; <b>one per application</b> (unique
 * {@code {tenantId, applicationId}}), so a re-accept is idempotent. The {@code PENDING_CONFIRMATION →
 * OFFICIALLY_PLACED} transition + its {@code PlacementLifecycle} land in Story 7.4. Carries {@code @Version}.
 */
@Document("placementRecords")
@CompoundIndex(name = "uniq_tenant_application", def = "{'tenantId': 1, 'applicationId': 1}", unique = true)
public class PlacementRecord extends TenantAwareDocument {

    private String studentId;
    private String applicationId;
    private String company;
    private Double ctc;
    private String role;
    private Instant joiningDate;
    private PlacementStatus status = PlacementStatus.PENDING_CONFIRMATION;
    @Version
    private Long version;

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public Double getCtc() {
        return ctc;
    }

    public void setCtc(Double ctc) {
        this.ctc = ctc;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(Instant joiningDate) {
        this.joiningDate = joiningDate;
    }

    public PlacementStatus getStatus() {
        return status;
    }

    public void setStatus(PlacementStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
