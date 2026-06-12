package com.campusconnect.common.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RefreshToken;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.AbstractMongoIT;
import com.campusconnect.common.repository.RefreshTokenRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtProperties;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Login credential/status gating, refresh rotation + re-gate, and logout for {@link AuthenticationService}. */
class AuthenticationServiceTest extends AbstractMongoIT {

    private static final String SECRET = "test-secret-0123456789-0123456789-abcd";

    TenantRepository tenantRepository;
    UserRepository userRepository;
    RefreshTokenRepository refreshTokenRepository;
    JwtService jwtService;
    final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    AuthenticationService service;

    @BeforeAll
    static void indexes() {
        ensureIndexes(RefreshToken.class);
    }

    @BeforeEach
    void setUp() {
        tenantRepository = new TenantRepository(mongoTemplate);
        userRepository = new UserRepository(mongoTemplate);
        refreshTokenRepository = new RefreshTokenRepository(mongoTemplate);
        JwtProperties props = new JwtProperties(SECRET, 30, 15, 7);
        jwtService = new JwtService(props);
        service = new AuthenticationService(
                tenantRepository, userRepository, passwordEncoder, jwtService, props, refreshTokenRepository);

        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), RefreshToken.class);
    }

    // ── login ──

    @Test
    void login_activeUser_issuesAccessAndRefreshToken() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);

        AuthResult result = service.login("vignan", "S@V.edu", "s3cret-pw", Role.STUDENT);

        assertThat(result.expiresInSeconds()).isEqualTo(30 * 60);
        assertThat(result.role()).isEqualTo(Role.STUDENT);
        // access token carries the right principal
        JwtService.AuthToken parsed = jwtService.parse(result.accessToken());
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
        assertThat(parsed.role()).isEqualTo(Role.STUDENT);
        // refresh token persisted
        assertThat(refreshTokenRepository.findByToken(result.refreshTokenValue())).isPresent();
    }

    @Test
    void login_unknownCollege_isInvalidCredentials() {
        assertCode(() -> service.login("nope", "a@b.edu", "x", Role.STUDENT), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_unknownEmail_isInvalidCredentials() {
        seedTenant("vignan");
        assertCode(() -> service.login("vignan", "ghost@v.edu", "x", Role.STUDENT), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_wrongPassword_isInvalidCredentials() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        assertCode(() -> service.login("vignan", "s@v.edu", "WRONG", Role.STUDENT), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_wrongPortalRole_isInvalidCredentials() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "r@v.edu", "s3cret-pw", Role.RECRUITER, AccountStatus.ACTIVE);
        // correct credentials, but logging in at the STUDENT portal
        assertCode(() -> service.login("vignan", "r@v.edu", "s3cret-pw", Role.STUDENT), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_pendingVerification_isEmailNotVerified() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.PENDING_VERIFICATION);
        assertCode(() -> service.login("vignan", "s@v.edu", "s3cret-pw", Role.STUDENT), ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    void login_pendingApproval_isRecruiterNotApproved() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "r@v.edu", "s3cret-pw", Role.RECRUITER, AccountStatus.PENDING_APPROVAL);
        assertCode(() -> service.login("vignan", "r@v.edu", "s3cret-pw", Role.RECRUITER), ErrorCode.RECRUITER_NOT_APPROVED);
    }

    @Test
    void login_rejected_isAccountInactive() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "r@v.edu", "s3cret-pw", Role.RECRUITER, AccountStatus.REJECTED);
        assertCode(() -> service.login("vignan", "r@v.edu", "s3cret-pw", Role.RECRUITER), ErrorCode.ACCOUNT_INACTIVE);
    }

    @Test
    void login_deactivated_isAccountInactive() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.DEACTIVATED);
        assertCode(() -> service.login("vignan", "s@v.edu", "s3cret-pw", Role.STUDENT), ErrorCode.ACCOUNT_INACTIVE);
    }

    // ── refresh ──

    @Test
    void refresh_rotatesToken_andInvalidatesOld() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        AuthResult login = service.login("vignan", "s@v.edu", "s3cret-pw", Role.STUDENT);

        AuthResult refreshed = service.refresh(login.refreshTokenValue());

        assertThat(refreshed.refreshTokenValue()).isNotEqualTo(login.refreshTokenValue()); // rotated
        assertThat(refreshTokenRepository.findByToken(refreshed.refreshTokenValue())).isPresent();
        // replay of the old token now fails
        assertCode(() -> service.refresh(login.refreshTokenValue()), ErrorCode.INVALID_TOKEN);
    }

    @Test
    void refresh_unknownToken_isInvalidToken() {
        assertCode(() -> service.refresh("no-such-token"), ErrorCode.INVALID_TOKEN);
    }

    @Test
    void refresh_expiredToken_isInvalidToken() {
        String tenantId = seedTenant("vignan");
        String userId = seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        RefreshToken expired = new RefreshToken();
        expired.setToken("stale");
        expired.setUserId(userId);
        expired.setTenantId(tenantId);
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        refreshTokenRepository.save(expired);

        assertCode(() -> service.refresh("stale"), ErrorCode.INVALID_TOKEN);
    }

    @Test
    void refresh_reGatesDeactivatedUser() {
        String tenantId = seedTenant("vignan");
        String userId = seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        AuthResult login = service.login("vignan", "s@v.edu", "s3cret-pw", Role.STUDENT);

        // admin deactivates the user mid-session
        User u = userRepository.findById(userId).orElseThrow();
        u.setAccountStatus(AccountStatus.DEACTIVATED);
        userRepository.save(u);

        assertCode(() -> service.refresh(login.refreshTokenValue()), ErrorCode.ACCOUNT_INACTIVE);
    }

    @Test
    void login_suspendedTenant_isForbidden() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        suspend(tenantId);

        assertCode(() -> service.login("vignan", "s@v.edu", "s3cret-pw", Role.STUDENT), ErrorCode.FORBIDDEN);
    }

    @Test
    void refresh_afterTenantSuspended_isForbidden() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        AuthResult login = service.login("vignan", "s@v.edu", "s3cret-pw", Role.STUDENT);

        suspend(tenantId); // college suspended mid-session

        assertCode(() -> service.refresh(login.refreshTokenValue()), ErrorCode.FORBIDDEN);
    }

    @Test
    void refresh_tokenTenantMismatch_isInvalidToken() {
        String tenantId = seedTenant("vignan");
        String userId = seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        // a token whose stored tenant does not match the user's tenant
        RefreshToken forged = new RefreshToken();
        forged.setToken("forged");
        forged.setUserId(userId);
        forged.setTenantId("some-other-tenant");
        forged.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        refreshTokenRepository.save(forged);

        assertCode(() -> service.refresh("forged"), ErrorCode.INVALID_TOKEN);
    }

    // ── logout ──

    @Test
    void logout_invalidatesRefreshToken() {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        AuthResult login = service.login("vignan", "s@v.edu", "s3cret-pw", Role.STUDENT);

        service.logout(login.refreshTokenValue());

        assertCode(() -> service.refresh(login.refreshTokenValue()), ErrorCode.INVALID_TOKEN);
    }

    @Test
    void logout_isIdempotent_forUnknownToken() {
        service.logout("never-existed"); // no throw
        service.logout(null);            // no throw
    }

    // ── helpers ──

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private void suspend(String tenantId) {
        Tenant t = tenantRepository.findById(tenantId).orElseThrow();
        t.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(t);
    }

    private String seedUser(String tenantId, String email, String password, Role role, AccountStatus status) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(role);
        u.setAccountStatus(status);
        return userRepository.save(u).getId();
    }

    private static void assertCode(org.junit.jupiter.api.function.ThrowingSupplier<?> call, ErrorCode expected) {
        assertThatThrownBy(call::get)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
