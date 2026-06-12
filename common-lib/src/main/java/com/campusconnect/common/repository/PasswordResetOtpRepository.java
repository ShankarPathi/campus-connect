package com.campusconnect.common.repository;

import com.campusconnect.common.domain.PasswordResetOtp;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for the {@code passwordResetOtps} collection (Story 2.4). MongoTemplate-backed
 * {@code @Repository} (style of {@link EmailVerifyTokenRepository}). Lookups are by {@code userId}
 * (the user is resolved from college + email first), and there is one active OTP per user.
 */
@Repository
public class PasswordResetOtpRepository {

    private final MongoTemplate mongoTemplate;

    public PasswordResetOtpRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public PasswordResetOtp save(PasswordResetOtp otp) {
        Instant now = Instant.now();
        if (otp.getCreatedAt() == null) {
            otp.setCreatedAt(now);
        }
        otp.setUpdatedAt(now);
        return mongoTemplate.save(otp);
    }

    public Optional<PasswordResetOtp> findByUserId(String userId) {
        return Optional.ofNullable(mongoTemplate.findOne(byUserId(userId), PasswordResetOtp.class));
    }

    public void deleteByUserId(String userId) {
        mongoTemplate.remove(byUserId(userId), PasswordResetOtp.class);
    }

    private static Query byUserId(String userId) {
        return new Query(Criteria.where("userId").is(userId));
    }
}
