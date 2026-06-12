package com.campusconnect.common.repository;

import com.campusconnect.common.domain.RefreshToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Round-trip, atomic single-use consume (rotation), and unique-token enforcement. */
class RefreshTokenRepositoryTest extends AbstractMongoIT {

    RefreshTokenRepository repository;

    @BeforeAll
    static void indexes() {
        ensureIndexes(RefreshToken.class);
    }

    @BeforeEach
    void setUp() {
        repository = new RefreshTokenRepository(mongoTemplate);
        mongoTemplate.remove(new Query(), RefreshToken.class);
    }

    @Test
    void save_thenFindByToken_roundTrips() {
        repository.save(token("tok-1", "user-1", "tenant-a"));

        RefreshToken found = repository.findByToken("tok-1").orElseThrow();
        assertThat(found.getUserId()).isEqualTo("user-1");
        assertThat(found.getTenantId()).isEqualTo("tenant-a");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findAndDeleteByToken_isAtomicSingleUse() {
        repository.save(token("tok-2", "user-2", "tenant-a"));

        assertThat(repository.findAndDeleteByToken("tok-2")).isPresent();
        // second consume of the same token finds nothing (rotation / replay protection)
        assertThat(repository.findAndDeleteByToken("tok-2")).isEmpty();
    }

    @Test
    void deleteByUserId_removesAllUserSessions() {
        repository.save(token("a", "user-3", "tenant-a"));
        repository.save(token("b", "user-3", "tenant-a"));

        repository.deleteByUserId("user-3");

        assertThat(repository.findByToken("a")).isEmpty();
        assertThat(repository.findByToken("b")).isEmpty();
    }

    @Test
    void duplicateToken_violatesUniqueIndex() {
        repository.save(token("dup", "user-4", "tenant-a"));

        assertThatThrownBy(() -> repository.save(token("dup", "user-5", "tenant-b")))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private static RefreshToken token(String value, String userId, String tenantId) {
        RefreshToken t = new RefreshToken();
        t.setToken(value);
        t.setUserId(userId);
        t.setTenantId(tenantId);
        t.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        return t;
    }
}
