package com.campusconnect.common.email;

import com.campusconnect.common.domain.EmailVerifyToken;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.AbstractMongoIT;
import com.campusconnect.common.repository.EmailVerifyTokenRepository;
import com.campusconnect.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Issue/consume semantics: round-trip identity, single-use, and expiry handling. */
class EmailVerificationServiceTest extends AbstractMongoIT {

    EmailVerifyTokenRepository tokenRepository;
    EmailVerificationService service;

    @BeforeAll
    static void indexes() {
        ensureIndexes(EmailVerifyToken.class);
    }

    @BeforeEach
    void setUp() {
        tokenRepository = new EmailVerifyTokenRepository(mongoTemplate);
        service = new EmailVerificationService(tokenRepository, Duration.ofHours(24));
        mongoTemplate.remove(new Query(), EmailVerifyToken.class);
    }

    @Test
    void issueThenConsume_returnsIdentity_andIsSingleUse() {
        String token = service.issueToken("user-1", "tenant-a");
        assertThat(token).isNotBlank();

        EmailVerificationService.VerifiedToken verified = service.verifyAndConsume(token);
        assertThat(verified.userId()).isEqualTo("user-1");
        assertThat(verified.tenantId()).isEqualTo("tenant-a");

        // second consume → invalid (single-use)
        assertThatThrownBy(() -> service.verifyAndConsume(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFY_TOKEN_INVALID);
    }

    @Test
    void issuedToken_expiresApproximately24hFromNow() {
        Instant before = Instant.now();
        String token = service.issueToken("user-ttl", "tenant-a");

        EmailVerifyToken persisted = tokenRepository.findByToken(token).orElseThrow();
        // expiresAt must be ~24h ahead (guards against a regression to a shorter/longer default TTL)
        assertThat(persisted.getExpiresAt())
                .isBetween(before.plus(23, ChronoUnit.HOURS).plus(59, ChronoUnit.MINUTES),
                        before.plus(24, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES));
    }

    @Test
    void unknownToken_throwsInvalid() {
        assertThatThrownBy(() -> service.verifyAndConsume("no-such-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFY_TOKEN_INVALID);
    }

    @Test
    void expiredToken_throwsInvalid_andIsDeleted() {
        // persist a token already in the past (bypass issueToken so we control expiresAt)
        EmailVerifyToken expired = new EmailVerifyToken();
        expired.setToken("stale");
        expired.setUserId("user-2");
        expired.setTenantId("tenant-a");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        tokenRepository.save(expired);

        assertThatThrownBy(() -> service.verifyAndConsume("stale"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFY_TOKEN_INVALID);

        assertThat(tokenRepository.findByToken("stale")).isEmpty();
    }
}
