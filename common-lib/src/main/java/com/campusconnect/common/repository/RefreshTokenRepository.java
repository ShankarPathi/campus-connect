package com.campusconnect.common.repository;

import com.campusconnect.common.domain.RefreshToken;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for the {@code refreshTokens} collection. A {@link MongoTemplate}-backed {@code @Repository}
 * (style of {@link EmailVerifyTokenRepository}). Not tenant-aware: a refresh token is looked up globally
 * by its opaque value, which is itself the secret. The unique-token and TTL indexes are declared on
 * {@link RefreshToken} and built by {@code auto-index-creation}.
 */
@Repository
public class RefreshTokenRepository {

    private final MongoTemplate mongoTemplate;

    public RefreshTokenRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public RefreshToken save(RefreshToken token) {
        Instant now = Instant.now();
        if (token.getCreatedAt() == null) {
            token.setCreatedAt(now);
        }
        token.setUpdatedAt(now);
        return mongoTemplate.save(token);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return Optional.ofNullable(mongoTemplate.findOne(byToken(token), RefreshToken.class));
    }

    /**
     * Atomically remove the token and return it (empty if absent). The rotation / single-use primitive:
     * two concurrent refreshes cannot both observe the same token, and a replayed (already-rotated)
     * token is simply not found.
     */
    public Optional<RefreshToken> findAndDeleteByToken(String token) {
        return Optional.ofNullable(mongoTemplate.findAndRemove(byToken(token), RefreshToken.class));
    }

    public void deleteByToken(String token) {
        mongoTemplate.remove(byToken(token), RefreshToken.class);
    }

    public void deleteByUserId(String userId) {
        mongoTemplate.remove(new Query(Criteria.where("userId").is(userId)), RefreshToken.class);
    }

    private static Query byToken(String token) {
        return new Query(Criteria.where("token").is(token));
    }
}
