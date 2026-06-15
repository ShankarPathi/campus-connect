package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A student's application to a {@link Drive} (Epic 5, FR-15). Tenant-scoped and owned by exactly one
 * student ({@code studentId} = the authenticated principal). Story 5.3 introduces the <b>read side</b>
 * only — the pre-apply transparency list reads it for the "Applied" grouping and the engine's
 * no-duplicate rule. The apply <b>write</b> (create on a passing eligibility check, snapshot the active
 * résumé into {@code resumeSnapshotKey}, and the <b>unique</b> {@code {tenantId, studentId, driveId}}
 * index that makes apply idempotent) is Story 5.4 — which also extends this document with the
 * snapshot/round/offer fields.
 *
 * <p>The read index here is the non-unique {@code {tenantId, studentId}} (the "my applications" query);
 * the unique idempotency index is added with the write in 5.4.
 */
@Document("applications")
@CompoundIndex(name = "idx_tenant_student", def = "{'tenantId': 1, 'studentId': 1}")
public class Application extends TenantAwareDocument {

    private String studentId;
    private String driveId;
    private ApplicationStatus status = ApplicationStatus.APPLIED;
    private Instant appliedAt;

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getDriveId() {
        return driveId;
    }

    public void setDriveId(String driveId) {
        this.driveId = driveId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }
}
