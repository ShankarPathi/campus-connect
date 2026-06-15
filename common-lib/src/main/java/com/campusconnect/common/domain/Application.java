package com.campusconnect.common.domain;

import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A student's application to a {@link Drive} (Epic 5, FR-15). Tenant-scoped and owned by exactly one
 * student ({@code studentId} = the authenticated principal). Story 5.3 introduced the read side (the
 * pre-apply "Applied" grouping); Story 5.4 adds the <b>write</b> — created on a passing eligibility
 * {@code check}, with the active résumé's {@code s3Key} frozen into {@code resumeSnapshotKey}.
 *
 * <p><b>Idempotent apply:</b> the unique {@code {tenantId, studentId, driveId}} index makes a duplicate
 * application impossible — a concurrent double-submit fails at the DB ({@code DuplicateKeyException} →
 * 409), so there is never a second row. The single compound index also serves the "my applications"
 * read via its {@code {tenantId, studentId}} prefix. {@code resumeSnapshotKey} is internal — never
 * serialized to a client (recruiters read it via a short-lived pre-signed URL in a later epic). The
 * {@code @Version} field is optimistic locking for the Epic 6–7 status transitions ({@code Application}
 * is the first genuinely concurrent-write collection).
 */
@Document("applications")
@CompoundIndex(name = "uniq_tenant_student_drive",
        def = "{'tenantId': 1, 'studentId': 1, 'driveId': 1}", unique = true)
public class Application extends TenantAwareDocument {

    private String studentId;
    private String driveId;
    private ApplicationStatus status = ApplicationStatus.APPLIED;
    private Instant appliedAt;
    /** The active résumé's {@code s3Key}, frozen at apply time (FR-15). Internal — never serialized. */
    private String resumeSnapshotKey;
    @Version
    private Long version;

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

    public String getResumeSnapshotKey() {
        return resumeSnapshotKey;
    }

    public void setResumeSnapshotKey(String resumeSnapshotKey) {
        this.resumeSnapshotKey = resumeSnapshotKey;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
