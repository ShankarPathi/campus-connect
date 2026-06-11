package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Tenant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for the {@code tenants} collection. <b>Not</b> tenant-aware — the platform admin manages
 * tenants globally, so reads/writes are NOT scoped to a {@code tenantId}. A MongoTemplate-backed
 * {@code @Repository} component (not a Spring Data interface) so it is found by the services'
 * {@code com.campusconnect} component scan.
 *
 * <p>The unique {@code slug} index is declared via {@code @Indexed(unique = true)} on
 * {@link Tenant#getSlug()} and created by {@code spring.data.mongodb.auto-index-creation: true}.
 */
@Repository
public class TenantRepository {

    private final MongoTemplate mongoTemplate;

    public TenantRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Tenant save(Tenant tenant) {
        if (tenant.getSlug() == null || tenant.getSlug().isBlank()) {
            throw new IllegalArgumentException("Tenant slug must not be null or blank");
        }
        Instant now = Instant.now();
        if (tenant.getCreatedAt() == null) {
            tenant.setCreatedAt(now);
        }
        tenant.setUpdatedAt(now);
        return mongoTemplate.save(tenant);
    }

    public Optional<Tenant> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, Tenant.class));
    }

    public Optional<Tenant> findBySlug(String slug) {
        return Optional.ofNullable(mongoTemplate.findOne(bySlug(slug), Tenant.class));
    }

    public boolean existsBySlug(String slug) {
        return mongoTemplate.exists(bySlug(slug), Tenant.class);
    }

    private static Query bySlug(String slug) {
        return new Query(Criteria.where("slug").is(slug));
    }
}
