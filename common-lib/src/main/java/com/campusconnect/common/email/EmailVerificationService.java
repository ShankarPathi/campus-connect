package com.campusconnect.common.email;

import com.campusconnect.common.domain.EmailVerifyToken;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.EmailVerifyTokenRepository;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues and consumes single-use, expiring email-verification tokens (Story 2.1). The token is an
 * opaque 256-bit random value (its secrecy is the security); it is persisted to
 * {@code emailVerifyTokens} with a 24h TTL and reused for password-reset OTPs in Story 2.4.
 *
 * <p>The post-verification action (activate a student vs. move a recruiter to PENDING_APPROVAL)
 * belongs to the calling service, so {@link #verifyAndConsume} returns only the verified identity.
 */
@Service
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final EmailVerifyTokenRepository tokenRepository;
    private final Duration tokenTtl;

    public EmailVerificationService(
            EmailVerifyTokenRepository tokenRepository,
            @Value("${app.verification.token-ttl:PT24H}") Duration tokenTtl) {
        this.tokenRepository = tokenRepository;
        this.tokenTtl = tokenTtl;
    }

    /** Generates, persists (with TTL), and returns a fresh single-use token for the user. */
    public String issueToken(String userId, String tenantId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = ENCODER.encodeToString(bytes);

        EmailVerifyToken token = new EmailVerifyToken();
        token.setToken(value);
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setExpiresAt(Instant.now().plus(tokenTtl));
        tokenRepository.save(token);
        return value;
    }

    /**
     * Validates a token and consumes it (single-use). Throws {@link ErrorCode#EMAIL_VERIFY_TOKEN_INVALID}
     * for an unknown, expired, or already-used token. Expiry is checked here (not only via the TTL
     * monitor, which lags ~60s).
     */
    public VerifiedToken verifyAndConsume(String token) {
        // Atomic delete-and-return guarantees single-use even under a concurrent double-click; an
        // expired token is also removed (it is invalid either way).
        EmailVerifyToken found = tokenRepository.findAndDeleteByToken(token)
                .orElseThrow(() -> invalid());

        if (found.getExpiresAt() == null || found.getExpiresAt().isBefore(Instant.now())) {
            throw invalid();
        }

        return new VerifiedToken(found.getUserId(), found.getTenantId());
    }

    /** Discard all outstanding tokens for a user (used to roll back a failed registration). */
    public void discardTokens(String userId) {
        tokenRepository.deleteByUserId(userId);
    }

    private static BusinessException invalid() {
        return new BusinessException(
                ErrorCode.EMAIL_VERIFY_TOKEN_INVALID, "Verification link is invalid or has expired");
    }

    /** The identity proven by a consumed verification token. */
    public record VerifiedToken(String userId, String tenantId) {
    }
}
