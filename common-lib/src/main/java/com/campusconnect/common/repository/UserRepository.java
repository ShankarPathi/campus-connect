package com.campusconnect.common.repository;

import com.campusconnect.common.domain.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for the {@code users} collection. <b>Not</b> tenant-aware: the platform-admin bootstrap
 * path (Story 1.6) has no tenant in context and sets the user's {@code tenantId} explicitly, which the
 * hardened {@code TenantAwareRepository.save} would reject. A MongoTemplate-backed {@code @Repository}
 * component (found by the {@code com.campusconnect} component scan). The {@code {tenantId, email}}
 * uniqueness is enforced by the compound index on {@link User}.
 */
@Repository
public class UserRepository {

    private final MongoTemplate mongoTemplate;

    public UserRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public User save(User user) {
        Instant now = Instant.now();
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(now);
        }
        user.setUpdatedAt(now);
        return mongoTemplate.save(user);
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, User.class));
    }

    /** Remove a user by id. Used to roll back a registration whose verification email failed to send. */
    public void deleteById(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), User.class);
    }

    public Optional<User> findByTenantIdAndEmail(String tenantId, String email) {
        return Optional.ofNullable(mongoTemplate.findOne(byTenantAndEmail(tenantId, email), User.class));
    }

    public boolean existsByTenantIdAndEmail(String tenantId, String email) {
        return mongoTemplate.exists(byTenantAndEmail(tenantId, email), User.class);
    }

    private static Query byTenantAndEmail(String tenantId, String email) {
        return new Query(Criteria.where("tenantId").is(tenantId).and("email").is(email));
    }
}
