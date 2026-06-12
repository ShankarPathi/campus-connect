package com.campusconnect.common.repository;

import com.campusconnect.common.domain.RecruiterProfile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code recruiterProfiles} collection. A {@link MongoTemplate}-backed
 * {@code @Repository} (same style as {@link UserRepository}) rather than a {@code TenantAwareRepository}:
 * recruiter registration is a <b>public</b> flow with no {@link com.campusconnect.common.tenancy.TenantContext},
 * so writes pass {@code tenantId} explicitly. The authenticated approval path scopes its reads by
 * passing the admin's tenant to {@link #findByUserIdAndTenantId} — a different tenant simply finds
 * nothing, which is the cross-tenant isolation guard.
 */
@Repository
public class RecruiterProfileRepository {

    private final MongoTemplate mongoTemplate;

    public RecruiterProfileRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public RecruiterProfile save(RecruiterProfile profile) {
        Instant now = Instant.now();
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }
        profile.setUpdatedAt(now);
        return mongoTemplate.save(profile);
    }

    /** Tenant-scoped lookup — the isolation guard for the approval path. */
    public Optional<RecruiterProfile> findByUserIdAndTenantId(String userId, String tenantId) {
        Query query = new Query(Criteria.where("userId").is(userId).and("tenantId").is(tenantId));
        return Optional.ofNullable(mongoTemplate.findOne(query, RecruiterProfile.class));
    }

    /** Batch-load profiles for a set of users within one tenant (used by the pending-recruiter list). */
    public List<RecruiterProfile> findByTenantIdAndUserIdIn(String tenantId, Collection<String> userIds) {
        Query query = new Query(Criteria.where("tenantId").is(tenantId).and("userId").in(userIds));
        return mongoTemplate.find(query, RecruiterProfile.class);
    }

    /**
     * Remove a recruiter's profile within a tenant — used to roll back a failed registration. Scoped
     * by {@code tenantId} like every other method here, so it cannot reach across tenants.
     */
    public void deleteByUserIdAndTenantId(String userId, String tenantId) {
        mongoTemplate.remove(
                new Query(Criteria.where("userId").is(userId).and("tenantId").is(tenantId)),
                RecruiterProfile.class);
    }
}
