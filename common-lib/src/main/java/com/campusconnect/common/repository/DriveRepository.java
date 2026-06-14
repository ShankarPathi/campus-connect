package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code drives} collection (Story 4.1). Extends {@link TenantAwareRepository}
 * because the drive flow is <b>authenticated</b> (the recruiter's {@link com.campusconnect.common.tenancy.TenantContext}
 * is bound), so every read/write auto-scopes to the current tenant — unlike the public
 * {@link RecruiterProfileRepository}.
 *
 * <p>Drives add a second ownership axis on top of the tenant filter: a recruiter sees only the drives
 * they created. {@link #findByCreatedBy} and {@link #findByIdAndCreatedBy} build on the tenant-scoped
 * {@code find}/{@code findById}, then narrow by {@code createdBy} — so another recruiter's drive (same
 * tenant) and another tenant's drive both resolve to empty (a 404 in the service).
 */
@Repository
public class DriveRepository extends TenantAwareRepository<Drive> {

    public DriveRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Drive.class);
    }

    /** The current tenant's drives created by this recruiter — the own-drives list. Tenant-scoped via {@code find}. */
    public List<Drive> findByCreatedBy(String recruiterId) {
        return find(new Query(Criteria.where("createdBy").is(recruiterId)));
    }

    /** One of this recruiter's own drives by id (tenant + owner scoped) — the 404 guard for get/edit. */
    public Optional<Drive> findByIdAndCreatedBy(String id, String recruiterId) {
        return findById(id).filter(d -> recruiterId.equals(d.getCreatedBy()));
    }

    /** The current tenant's drives in a given status — backs the College-Admin review queue (Story 4.3). */
    public List<Drive> findByStatus(DriveStatus status) {
        return find(new Query(Criteria.where("status").is(status)));
    }
}
