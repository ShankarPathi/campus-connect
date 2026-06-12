package com.campusconnect.common.repository;

import com.campusconnect.common.domain.EmailVerifyToken;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for the {@code emailVerifyTokens} collection. A {@link MongoTemplate}-backed
 * {@code @Repository} (not a Spring Data interface) so the services' {@code com.campusconnect}
 * component scan finds it — same style as {@link UserRepository} / {@link TenantRepository}.
 *
 * <p>Not tenant-aware: tokens are looked up globally by their opaque random value, which is itself
 * the secret. The unique {@code token} index and the TTL index are declared on
 * {@link EmailVerifyToken} and created by {@code spring.data.mongodb.auto-index-creation: true}.
 */
@Repository
public class EmailVerifyTokenRepository {

    private final MongoTemplate mongoTemplate;

    public EmailVerifyTokenRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public EmailVerifyToken save(EmailVerifyToken token) {
        Instant now = Instant.now();
        if (token.getCreatedAt() == null) {
            token.setCreatedAt(now);
        }
        token.setUpdatedAt(now);
        return mongoTemplate.save(token);
    }

    public Optional<EmailVerifyToken> findByToken(String token) {
        return Optional.ofNullable(mongoTemplate.findOne(byToken(token), EmailVerifyToken.class));
    }

    /**
     * Atomically remove the token and return it (empty if absent). This is the single-use primitive:
     * two concurrent verify requests cannot both observe the same token, because the delete-and-return
     * is one Mongo {@code findAndModify} operation. Used by {@code EmailVerificationService.verifyAndConsume}.
     */
    public Optional<EmailVerifyToken> findAndDeleteByToken(String token) {
        return Optional.ofNullable(mongoTemplate.findAndRemove(byToken(token), EmailVerifyToken.class));
    }

    public void deleteByToken(String token) {
        mongoTemplate.remove(byToken(token), EmailVerifyToken.class);
    }

    public void deleteByUserId(String userId) {
        mongoTemplate.remove(new Query(Criteria.where("userId").is(userId)), EmailVerifyToken.class);
    }

    private static Query byToken(String token) {
        return new Query(Criteria.where("token").is(token));
    }
}
