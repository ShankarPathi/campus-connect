package com.campusconnect.common.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RefreshToken;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ForbiddenException;
import com.campusconnect.common.repository.RefreshTokenRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtProperties;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared login / refresh / logout for every portal (Story 2.3, FR-5). Issues the existing access JWT,
 * adds rotating refresh tokens, and enforces the account-status gate built in Stories 2.1/2.2. Portal
 * controllers are thin wrappers over this service.
 */
@Service
public class AuthenticationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    /** A real BCrypt hash compared against when the user is unknown, to keep login timing constant. */
    private final String dummyHash;

    public AuthenticationService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshTokenRepository refreshTokenRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    /**
     * Authenticate for a specific portal role and start a session. Credential failures (unknown college,
     * unknown email, wrong password, wrong portal/role) all return a uniform 401 INVALID_CREDENTIALS so
     * nothing is enumerable; the status 403s are reached only after the password is verified, so account
     * state leaks only to the legitimate owner.
     */
    public AuthResult login(String collegeCode, String email, String rawPassword, Role expectedRole) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Tenant tenant = tenantRepository.findBySlug(collegeCode).orElse(null);
        User user = (tenant == null) ? null
                : userRepository.findByTenantIdAndEmail(tenant.getId(), normalizedEmail).orElse(null);

        // Always run exactly one BCrypt comparison (a dummy hash when the user is unknown) so login
        // timing does not reveal whether the college/email exists — closes the enumeration side-channel.
        boolean passwordMatches = passwordEncoder.matches(rawPassword, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !passwordMatches || user.getRole() != expectedRole) {
            throw invalidCredentials();
        }

        // Status + tenant gates run only after a correct password, so they leak state only to the owner.
        gateByStatus(user.getAccountStatus());
        requireActiveTenant(tenant);

        return startSession(user);
    }

    /**
     * Consume a refresh token (atomic, single-use), re-check the account is still ACTIVE, and issue a new
     * access token + a new (rotated) refresh token. A missing/expired/already-rotated token → 401.
     */
    public AuthResult refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw invalidToken();
        }
        RefreshToken existing = refreshTokenRepository.findAndDeleteByToken(refreshTokenValue)
                .orElseThrow(this::invalidToken);
        if (existing.getExpiresAt() == null || existing.getExpiresAt().isBefore(Instant.now())) {
            throw invalidToken();
        }
        User user = userRepository.findById(existing.getUserId()).orElseThrow(this::invalidToken);
        // defense-in-depth: the token's tenant must still match the user's tenant
        if (!Objects.equals(existing.getTenantId(), user.getTenantId())) {
            throw invalidToken();
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, "Account is not active");
        }
        // re-enforce tenant suspension — a session must not outlive its college's suspension
        Tenant tenant = tenantRepository.findById(user.getTenantId()).orElseThrow(this::invalidToken);
        requireActiveTenant(tenant);

        return startSession(user);
    }

    /** End a session — delete the refresh token. Idempotent: a null/unknown token is a no-op. */
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.deleteByToken(refreshTokenValue);
        }
    }

    // ── internals ──

    private AuthResult startSession(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole(), user.getTenantId());
        long expiresInSeconds = Duration.ofMinutes(jwtProperties.minutesFor(user.getRole())).toSeconds();

        RefreshToken refresh = new RefreshToken();
        refresh.setToken(randomToken());
        refresh.setUserId(user.getId());
        refresh.setTenantId(user.getTenantId());
        refresh.setExpiresAt(Instant.now().plus(Duration.ofDays(jwtProperties.refreshTokenDays())));
        refreshTokenRepository.save(refresh);

        return new AuthResult(accessToken, expiresInSeconds, user.getRole(),
                refresh.getToken(), user.getId(), user.getTenantId());
    }

    /** A user may not authenticate into a suspended college (mirrors the registration-time gate). */
    private void requireActiveTenant(Tenant tenant) {
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new ForbiddenException("This college is currently suspended");
        }
    }

    private void gateByStatus(AccountStatus status) {
        switch (status) {
            case ACTIVE -> { /* allowed */ }
            case PENDING_VERIFICATION ->
                    throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "Email not yet verified");
            case PENDING_APPROVAL ->
                    throw new BusinessException(ErrorCode.RECRUITER_NOT_APPROVED, "Account is awaiting approval");
            case REJECTED, DEACTIVATED ->
                    throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, "Account is not active");
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid or expired session");
    }
}
