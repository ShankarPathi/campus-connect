package com.campusconnect.common.repository;

import com.campusconnect.common.domain.PasswordResetOtp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trip, delete, and the "one active OTP per user" replace behaviour. */
class PasswordResetOtpRepositoryTest extends AbstractMongoIT {

    PasswordResetOtpRepository repository;

    @BeforeAll
    static void indexes() {
        ensureIndexes(PasswordResetOtp.class);
    }

    @BeforeEach
    void setUp() {
        repository = new PasswordResetOtpRepository(mongoTemplate);
        mongoTemplate.remove(new Query(), PasswordResetOtp.class);
    }

    @Test
    void save_thenFindByUserId_roundTrips() {
        repository.save(otp("user-1", "123456"));

        PasswordResetOtp found = repository.findByUserId("user-1").orElseThrow();
        assertThat(found.getOtp()).isEqualTo("123456");
        assertThat(found.getAttempts()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void deleteByUserId_removesOtp() {
        repository.save(otp("user-1", "123456"));

        repository.deleteByUserId("user-1");

        assertThat(repository.findByUserId("user-1")).isEmpty();
    }

    @Test
    void replace_modelsOneActiveOtpPerUser() {
        repository.save(otp("user-1", "111111"));
        repository.deleteByUserId("user-1");
        repository.save(otp("user-1", "222222"));

        assertThat(repository.findByUserId("user-1").orElseThrow().getOtp()).isEqualTo("222222");
    }

    private static PasswordResetOtp otp(String userId, String code) {
        PasswordResetOtp o = new PasswordResetOtp();
        o.setUserId(userId);
        o.setTenantId("tenant-a");
        o.setOtp(code);
        o.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        return o;
    }
}
