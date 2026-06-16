package com.campusconnect.common.repository;

import com.campusconnect.common.domain.PlacementRecord;
import com.campusconnect.common.domain.PlacementStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code placementRecords} collection (Story 7.3, FR-24). Extends
 * {@link TenantAwareRepository}, so every read auto-scopes to the current tenant. {@link #findByApplicationId}
 * is the accept idempotency backstop (paired with the unique {@code {tenantId, applicationId}} index);
 * {@link #findByStudentId} backs the admin confirmation (7.4) and placement reports (Epic 8).
 */
@Repository
public class PlacementRecordRepository extends TenantAwareRepository<PlacementRecord> {

    public PlacementRecordRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, PlacementRecord.class);
    }

    /** The placement record for one application, if any (tenant-scoped via {@code find}). */
    public Optional<PlacementRecord> findByApplicationId(String applicationId) {
        return find(new Query(Criteria.where("applicationId").is(applicationId))).stream().findFirst();
    }

    /** The current tenant's placement records for one student. */
    public List<PlacementRecord> findByStudentId(String studentId) {
        return find(new Query(Criteria.where("studentId").is(studentId)));
    }

    /** The current tenant's placement records in the given status — the admin confirmation queue (Story 7.4). */
    public List<PlacementRecord> findByStatus(PlacementStatus status) {
        return find(new Query(Criteria.where("status").is(status)));
    }

    /** Count of the current tenant's placement records in the given status — the Story 8.4 dashboard (placed = OFFICIALLY_PLACED). */
    public long countByStatus(PlacementStatus status) {
        return mongoTemplate.count(withTenant(new Query(Criteria.where("status").is(status))), type);
    }

    /**
     * Count of <b>distinct students</b> with a placement record in the given status, tenant-scoped — the Story 8.4
     * dashboard's "placed students". A record is one-per-application ({@code {tenant, applicationId}} unique), so a
     * student placed via two applications must not be double-counted; this de-dups on {@code studentId}. A
     * {@code findDistinct} (not a group-by aggregation pipeline — those are Story 8.5).
     */
    public long countDistinctStudentsByStatus(PlacementStatus status) {
        return mongoTemplate.findDistinct(
                withTenant(new Query(Criteria.where("status").is(status))), "studentId", type, String.class).size();
    }
}
