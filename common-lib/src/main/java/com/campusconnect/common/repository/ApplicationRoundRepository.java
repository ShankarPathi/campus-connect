package com.campusconnect.common.repository;

import com.campusconnect.common.domain.ApplicationRound;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the {@code applicationRounds} collection (Story 6.3). Extends {@link TenantAwareRepository}
 * — the recruiter is authenticated, so every read/write auto-scopes to the current tenant. The per-student
 * round rows are looked up by application (a student's rounds), by {@code (driveId, roundOrder)} (everyone in
 * round N), and existence-checked per {@code (applicationId, roundOrder)} for idempotent assignment.
 */
@Repository
public class ApplicationRoundRepository extends TenantAwareRepository<ApplicationRound> {

    public ApplicationRoundRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, ApplicationRound.class);
    }

    /** All of one application's round rows (a student's interview track). Tenant-scoped via {@code find}. */
    public List<ApplicationRound> findByApplicationId(String applicationId) {
        return find(new Query(Criteria.where("applicationId").is(applicationId)));
    }

    /** Every applicant in a given round of a drive — backs the round roster + assigned counts. */
    public List<ApplicationRound> findByDriveIdAndRoundOrder(String driveId, int roundOrder) {
        return find(new Query(Criteria.where("driveId").is(driveId).and("roundOrder").is(roundOrder)));
    }

    /** All round rows of a drive (across rounds) — the restructure guard reads results across the drive. */
    public List<ApplicationRound> findByDriveId(String driveId) {
        return find(new Query(Criteria.where("driveId").is(driveId)));
    }

    /** Whether this application already has a row for this round (idempotent round-1 assignment). */
    public boolean existsByApplicationIdAndRoundOrder(String applicationId, int roundOrder) {
        return !find(new Query(
                Criteria.where("applicationId").is(applicationId).and("roundOrder").is(roundOrder))).isEmpty();
    }

    /** The number of applicants assigned to a given round of a drive (the GET assigned-count). */
    public long countByDriveIdAndRoundOrder(String driveId, int roundOrder) {
        return mongoTemplate.count(
                withTenant(new Query(Criteria.where("driveId").is(driveId).and("roundOrder").is(roundOrder))), type);
    }
}
