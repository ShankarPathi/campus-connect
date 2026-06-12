package com.campusconnect.common.repository;

import com.campusconnect.common.domain.EmailVerifyToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Round-trip, single-use deletion, and unique-token enforcement for {@link EmailVerifyTokenRepository}. */
class EmailVerifyTokenRepositoryTest extends AbstractMongoIT {

    EmailVerifyTokenRepository repository;

    @BeforeAll
    static void indexes() {
        ensureIndexes(EmailVerifyToken.class);
    }

    @BeforeEach
    void setUp() {
        repository = new EmailVerifyTokenRepository(mongoTemplate);
        mongoTemplate.remove(new Query(), EmailVerifyToken.class);
    }

    @Test
    void save_thenFindByToken_roundTrips() {
        EmailVerifyToken saved = repository.save(token("tok-1", "user-1", "tenant-a"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        EmailVerifyToken found = repository.findByToken("tok-1").orElseThrow();
        assertThat(found.getUserId()).isEqualTo("user-1");
        assertThat(found.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void deleteByToken_makesItSingleUse() {
        repository.save(token("tok-2", "user-2", "tenant-a"));

        repository.deleteByToken("tok-2");

        assertThat(repository.findByToken("tok-2")).isEmpty();
    }

    @Test
    void deleteByUserId_removesAllUserTokens() {
        repository.save(token("tok-3a", "user-3", "tenant-a"));
        repository.save(token("tok-3b", "user-3", "tenant-a"));

        repository.deleteByUserId("user-3");

        assertThat(repository.findByToken("tok-3a")).isEmpty();
        assertThat(repository.findByToken("tok-3b")).isEmpty();
    }

    @Test
    void duplicateToken_violatesUniqueIndex() {
        repository.save(token("dup", "user-4", "tenant-a"));

        assertThatThrownBy(() -> repository.save(token("dup", "user-5", "tenant-b")))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private static EmailVerifyToken token(String value, String userId, String tenantId) {
        EmailVerifyToken t = new EmailVerifyToken();
        t.setToken(value);
        t.setUserId(userId);
        t.setTenantId(tenantId);
        t.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return t;
    }
}
