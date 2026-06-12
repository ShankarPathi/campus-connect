package com.campusconnect.common.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.PasswordResetOtp;
import com.campusconnect.common.domain.RefreshToken;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.ratelimit.RateLimiter;
import com.campusconnect.common.repository.AbstractMongoIT;
import com.campusconnect.common.repository.PasswordResetOtpRepository;
import com.campusconnect.common.repository.RefreshTokenRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reset-via-OTP (happy/expired/wrong/locked/unknown), authenticated change, and session-kill. */
class PasswordServiceTest extends AbstractMongoIT {

    TenantRepository tenantRepository;
    UserRepository userRepository;
    PasswordResetOtpRepository otpRepository;
    RefreshTokenRepository refreshTokenRepository;
    final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    /** Records the OTP emails so tests can read the code. */
    static class RecordingEmail implements EmailService {
        final List<String> bodies = new ArrayList<>();
        @Override public void sendVerificationEmail(String to, String link) { }
        @Override public void sendEmail(String to, String subject, String body) { bodies.add(body); }
        String lastCode() {
            var m = java.util.regex.Pattern.compile("code is: (\\d{6})").matcher(bodies.get(bodies.size() - 1));
            return m.find() ? m.group(1) : null;
        }
    }
    RecordingEmail email;
    PasswordService service;

    @BeforeAll
    static void indexes() {
        ensureIndexes(PasswordResetOtp.class);
    }

    @BeforeEach
    void setUp() {
        tenantRepository = new TenantRepository(mongoTemplate);
        userRepository = new UserRepository(mongoTemplate);
        otpRepository = new PasswordResetOtpRepository(mongoTemplate);
        refreshTokenRepository = new RefreshTokenRepository(mongoTemplate);
        email = new RecordingEmail();
        // Generous request-limit (100/hr) so these OTP-logic tests are not coupled to the 2.5 throttle;
        // the throttle itself is covered by RateLimiterTest and the HTTP-layer throttle tests.
        service = new PasswordService(tenantRepository, userRepository, passwordEncoder, otpRepository,
                refreshTokenRepository, email, new RateLimiter(), Duration.ofMinutes(10), 5,
                100, Duration.ofHours(1));

        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), PasswordResetOtp.class);
        mongoTemplate.remove(new Query(), RefreshToken.class);
    }

    // ── forgot ──

    @Test
    void requestReset_storesOtp_andEmailsCode() {
        String t = seedTenant("vignan");
        String u = seedUser(t, "s@v.edu", "old-password");

        service.requestReset("vignan", "S@V.edu");

        assertThat(otpRepository.findByUserId(u)).isPresent();
        assertThat(email.lastCode()).matches("\\d{6}");
    }

    @Test
    void requestReset_unknownUser_isSilentNoOp() {
        seedTenant("vignan");
        service.requestReset("vignan", "ghost@v.edu");
        service.requestReset("nope", "x@y.edu");
        assertThat(mongoTemplate.findAll(PasswordResetOtp.class)).isEmpty();
        assertThat(email.bodies).isEmpty();
    }

    @Test
    void requestReset_replacesPriorOtp() {
        String t = seedTenant("vignan");
        seedUser(t, "s@v.edu", "old-password");

        service.requestReset("vignan", "s@v.edu");
        String first = email.lastCode();
        service.requestReset("vignan", "s@v.edu");

        assertThat(mongoTemplate.findAll(PasswordResetOtp.class)).hasSize(1);
        // the first code no longer works
        assertCode(() -> service.resetPassword("vignan", "s@v.edu", first, "brand-new-pw"), ErrorCode.OTP_INVALID);
    }

    // ── reset ──

    @Test
    void resetPassword_happyPath_updatesHash_consumesOtp_killsSessions() {
        String t = seedTenant("vignan");
        String u = seedUser(t, "s@v.edu", "old-password");
        seedRefreshToken(u, t); // an existing session
        service.requestReset("vignan", "s@v.edu");
        String code = email.lastCode();

        service.resetPassword("vignan", "s@v.edu", code, "brand-new-pw");

        User updated = userRepository.findById(u).orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-pw", updated.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("old-password", updated.getPasswordHash())).isFalse();
        assertThat(otpRepository.findByUserId(u)).isEmpty();          // single-use
        assertThat(mongoTemplate.findAll(RefreshToken.class)).isEmpty(); // sessions killed
    }

    @Test
    void resetPassword_consumedOtp_cannotBeReused() {
        String t = seedTenant("vignan");
        seedUser(t, "s@v.edu", "old-password");
        service.requestReset("vignan", "s@v.edu");
        String code = email.lastCode();
        service.resetPassword("vignan", "s@v.edu", code, "brand-new-pw");

        assertCode(() -> service.resetPassword("vignan", "s@v.edu", code, "another-pw"), ErrorCode.OTP_INVALID);
    }

    @Test
    void resetPassword_wrongCode_locksAfterMaxAttempts() {
        String t = seedTenant("vignan");
        seedUser(t, "s@v.edu", "old-password");
        service.requestReset("vignan", "s@v.edu");
        String real = email.lastCode();

        for (int i = 0; i < 5; i++) {
            assertCode(() -> service.resetPassword("vignan", "s@v.edu", "000000", "brand-new-pw"), ErrorCode.OTP_INVALID);
        }
        // after 5 wrong attempts the OTP is gone — even the correct code fails now
        assertCode(() -> service.resetPassword("vignan", "s@v.edu", real, "brand-new-pw"), ErrorCode.OTP_INVALID);
    }

    @Test
    void resetPassword_expiredOtp_isOtpExpired() {
        String t = seedTenant("vignan");
        String u = seedUser(t, "s@v.edu", "old-password");
        PasswordResetOtp stale = new PasswordResetOtp();
        stale.setUserId(u);
        stale.setTenantId(t);
        stale.setOtp("123456");
        stale.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        otpRepository.save(stale);

        assertCode(() -> service.resetPassword("vignan", "s@v.edu", "123456", "brand-new-pw"), ErrorCode.OTP_EXPIRED);
    }

    @Test
    void resetPassword_unknownUserOrNoOtp_isUniformOtpInvalid() {
        String t = seedTenant("vignan");
        seedUser(t, "s@v.edu", "old-password"); // user exists but has no OTP on file
        assertCode(() -> service.resetPassword("vignan", "s@v.edu", "123456", "brand-new-pw"), ErrorCode.OTP_INVALID);
        assertCode(() -> service.resetPassword("vignan", "ghost@v.edu", "123456", "brand-new-pw"), ErrorCode.OTP_INVALID);
    }

    // ── change ──

    @Test
    void changePassword_happyPath_updatesHash_andKillsSessions() {
        String t = seedTenant("vignan");
        String u = seedUser(t, "s@v.edu", "old-password");
        seedRefreshToken(u, t);

        service.changePassword(u, "old-password", "brand-new-pw");

        User updated = userRepository.findById(u).orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-pw", updated.getPasswordHash())).isTrue();
        assertThat(mongoTemplate.findAll(RefreshToken.class)).isEmpty();
    }

    @Test
    void changePassword_wrongCurrent_isInvalidCredentials() {
        String t = seedTenant("vignan");
        String u = seedUser(t, "s@v.edu", "old-password");
        assertCode(() -> service.changePassword(u, "WRONG", "brand-new-pw"), ErrorCode.INVALID_CREDENTIALS);
    }

    // ── helpers ──

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedUser(String tenantId, String email, String password) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(Role.STUDENT);
        u.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(u).getId();
    }

    private void seedRefreshToken(String userId, String tenantId) {
        RefreshToken rt = new RefreshToken();
        rt.setToken("sess-" + userId);
        rt.setUserId(userId);
        rt.setTenantId(tenantId);
        rt.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        refreshTokenRepository.save(rt);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
