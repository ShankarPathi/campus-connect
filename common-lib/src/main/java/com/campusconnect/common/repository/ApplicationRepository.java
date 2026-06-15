package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Application;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
