package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code applications} collection (Story 5.3, read side). Extends
 * {@link TenantAwareRepository}, so every read auto-scopes to the current tenant (the student is always
 * authenticated). Story 5.3 uses only the reads here — the student's own applications (the "Applied"
 * grouping) and the {@code alreadyApplied} existence check (the engine's no-duplicate rule). The apply
 * <b>write</b> + the unique idempotency index are Story 5.4.
 */
@Repository
public class ApplicationRepository extends TenantAwareRepository<Application> {

    public ApplicationRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Application.class);
    }

    /** The current tenant's applications for one student — the "my applications" read. Tenant-scoped via {@code find}. */
    public List<Application> findByStudentId(String studentId) {
        return find(new Query(Criteria.where("studentId").is(studentId)));
    }

    /** Whether this student already has an application to this drive (the engine's no-duplicate rule). */
    public boolean existsByStudentIdAndDriveId(String studentId, String driveId) {
        return !find(new Query(
                Criteria.where("studentId").is(studentId).and("driveId").is(driveId))).isEmpty();
    }

    /**
     * One of this student's own applications by id (tenant + owner scoped) — the 404 guard for withdraw
     * (Story 5.5). Builds on the tenant-scoped {@code findById}, then narrows by {@code studentId}, so
     * another student's application (same tenant) and another tenant's both resolve to empty.
     */
    public Optional<Application> findByIdAndStudentId(String id, String studentId) {
        return findById(id).filter(a -> studentId.equals(a.getStudentId()));
    }

    /**
     * Applications to one drive in any of the given statuses — the recruiter applicant list with its status
     * filter (Story 6.1). Tenant-scoped via {@code find}; uses the {@code {tenantId, driveId, status}} index.
     * An empty status set short-circuits to no query (an empty {@code $in} would match nothing anyway).
     */
    public List<Application> findByDriveIdAndStatusIn(String driveId, Collection<ApplicationStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return find(new Query(Criteria.where("driveId").is(driveId).and("status").in(statuses)));
    }

    /**
     * One application by id <b>scoped to a drive</b> (tenant + drive scoped) — the 404 guard for the
     * recruiter résumé-snapshot fetch (Story 6.1). Builds on the tenant-scoped {@code findById}, then narrows
     * by {@code driveId}, so an application of another drive (even the same recruiter's) and another tenant's
     * both resolve to empty.
     */
    public Optional<Application> findByIdAndDriveId(String id, String driveId) {
        return findById(id).filter(a -> driveId.equals(a.getDriveId()));
    }
}
