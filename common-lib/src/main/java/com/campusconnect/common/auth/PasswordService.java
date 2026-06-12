package com.campusconnect.common.auth;

import com.campusconnect.common.domain.PasswordResetOtp;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.PasswordResetOtpRepository;
import com.campusconnect.common.repository.RefreshTokenRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Password reset (via emailed OTP) and authenticated change (Story 2.4, FR-5). Shared by every portal.
 * Both reset and change invalidate all of the user's refresh-token sessions (the 2.3 {@code deleteByUserId}
 * primitive). The OTP is single-use, expiring, and attempt-limited per code — but the per-OTP attempt lock
 * is only a speed-bump: it resets when a new OTP is requested, so genuine brute-force protection REQUIRES
 * the {@code forgot}/OTP request rate-limit of Story 2.5 (tracked in deferred-work as security-critical).
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RESET_SUBJECT = "Your Campus Connect password reset code";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetOtpRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final com.campusconnect.common.email.EmailService emailService;
    private final Duration otpTtl;
    private final int maxAttempts;

    public PasswordService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetOtpRepository otpRepository,
            RefreshTokenRepository refreshTokenRepository,
            com.campusconnect.common.email.EmailService emailService,
            @Value("${app.auth.otp-ttl:PT10M}") Duration otpTtl,
            @Value("${app.auth.otp-max-attempts:5}") int maxAttempts) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpRepository = otpRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailService = emailService;
        this.otpTtl = otpTtl;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Issue and email a reset OTP. Anti-enumeration: an unknown college/email is a silent no-op (the
     * caller always gets 200). A new request replaces any existing OTP for the user.
     */
    public void requestReset(String collegeCode, String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> maybeUser = tenantRepository.findBySlug(collegeCode)
                .flatMap(t -> userRepository.findByTenantIdAndEmail(t.getId(), normalizedEmail));
        if (maybeUser.isEmpty()) {
            return; // do not reveal whether the account exists
        }
        User user = maybeUser.get();

        otpRepository.deleteByUserId(user.getId()); // one active OTP per user
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetOtp otp = new PasswordResetOtp();
        otp.setUserId(user.getId());
        otp.setTenantId(user.getTenantId());
        otp.setOtp(code);
        otp.setAttempts(0);
        otp.setExpiresAt(Instant.now().plus(otpTtl));
        otpRepository.save(otp);

        try {
            emailService.sendEmail(normalizedEmail, RESET_SUBJECT, """
                    You requested a password reset for your Campus Connect account.

                    Your one-time code is: %s

                    It expires in %d minutes. If you did not request this, ignore this email.
                    """.formatted(code, otpTtl.toMinutes()));
        } catch (RuntimeException ex) {
            // Best-effort: a send failure must NOT change the response, or the 500-vs-200 difference
            // becomes an account-existence oracle (an unknown user already returns 200). The user can
            // retry; Epic 8's email outbox makes delivery reliable.
            log.warn("Failed to send password-reset code to a user (response unchanged)", ex);
        }
    }

    /**
     * Verify the OTP and set a new password. Uniform {@link ErrorCode#OTP_INVALID} for unknown
     * user/no-OTP/wrong-code; {@link ErrorCode#OTP_EXPIRED} for an expired code. After {@code maxAttempts}
     * wrong guesses the OTP is invalidated. On success the OTP is consumed and all sessions are killed.
     */
    public void resetPassword(String collegeCode, String email, String otpCode, String newPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = tenantRepository.findBySlug(collegeCode)
                .flatMap(t -> userRepository.findByTenantIdAndEmail(t.getId(), normalizedEmail))
                .orElseThrow(this::otpInvalid);

        PasswordResetOtp otp = otpRepository.findByUserId(user.getId()).orElseThrow(this::otpInvalid);

        if (otp.getExpiresAt() == null || otp.getExpiresAt().isBefore(Instant.now())) {
            otpRepository.deleteByUserId(user.getId());
            throw new BusinessException(ErrorCode.OTP_EXPIRED, "The reset code has expired");
        }

        if (!otp.getOtp().equals(otpCode)) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= maxAttempts) {
                otpRepository.deleteByUserId(user.getId()); // lock out after too many wrong guesses
            } else {
                otpRepository.save(otp);
            }
            throw otpInvalid();
        }

        applyNewPassword(user, newPassword);
        otpRepository.deleteByUserId(user.getId()); // single-use
    }

    /**
     * Authenticated change. Verifies the current password (else {@link ErrorCode#INVALID_CREDENTIALS}),
     * sets the new one, and kills all sessions.
     */
    public void changePassword(String userId, String currentPassword, String newPassword) {
        // Defense-in-depth: the security chain authenticates this endpoint, so userId should never be
        // null — but never call findById(null) (it throws rather than returning empty).
        if (userId == null || userId.isBlank()) {
            throw invalidCredentials();
        }
        User user = userRepository.findById(userId).orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        applyNewPassword(user, newPassword);
    }

    // ── internals ──

    private void applyNewPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.deleteByUserId(user.getId()); // log out everywhere on a password change
    }

    private BusinessException otpInvalid() {
        return new BusinessException(ErrorCode.OTP_INVALID, "The reset code is invalid");
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
    }
}
