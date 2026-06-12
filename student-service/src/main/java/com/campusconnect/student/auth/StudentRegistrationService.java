package com.campusconnect.student.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.email.EmailVerificationService;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.DuplicateResourceException;
import com.campusconnect.common.exception.ForbiddenException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Student self-registration and email verification (Story 2.1, FR-4).
 *
 * <p>Tenant is resolved at the edge from the {@code collegeCode} (tenant slug) since registration is
 * public (no JWT). Mirrors {@code CollegeAdminBootstrapService}: normalize email, 404-before-409,
 * BCrypt via the shared encoder. The verification token lives in its own expiring collection; the
 * post-verify activation (STUDENT → ACTIVE) is owned here, not by the shared token service.
 */
@Service
public class StudentRegistrationService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;
    private final String verificationBaseUrl;

    public StudentRegistrationService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            EmailService emailService,
            @Value("${app.verification.base-url}") String verificationBaseUrl) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.emailService = emailService;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    /**
     * Register a new student under the college named by {@code collegeCode}. Creates an inactive
     * (PENDING_VERIFICATION) account, issues a verification token, and emails the activation link.
     * Persists the user and token before sending the email.
     */
    public StudentRegistrationResponse register(RegisterStudentRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Resolve tenant at the edge (404 before 409) — do not reveal internal ids.
        Tenant tenant = tenantRepository.findBySlug(request.collegeCode())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown college: " + request.collegeCode()));
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new ForbiddenException("This college is not currently accepting registrations");
        }

        if (userRepository.existsByTenantIdAndEmail(tenant.getId(), email)) {
            throw new DuplicateResourceException(
                    ErrorCode.EMAIL_ALREADY_EXISTS, "Email already in use for this college: " + email);
        }

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.STUDENT);
        user.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        User saved = userRepository.save(user);
        String token = emailVerificationService.issueToken(saved.getId(), tenant.getId());

        // Persist-before-send (AC11), but if the send fails, roll back the user + token so the address
        // is not orphaned in PENDING_VERIFICATION (which the 409 dup-guard would then lock out forever).
        // The user simply retries. The durable async outbox + retry is Epic 8.
        try {
            emailService.sendVerificationEmail(email, buildVerificationLink(token));
        } catch (RuntimeException ex) {
            emailVerificationService.discardTokens(saved.getId());
            userRepository.deleteById(saved.getId());
            throw new BusinessException(
                    ErrorCode.EMAIL_SEND_FAILED, "Could not send the verification email — please try again");
        }

        return StudentRegistrationResponse.from(saved);
    }

    /** Verify an email token and activate the account (PENDING_VERIFICATION → ACTIVE). Idempotent. */
    public void verifyEmail(String token) {
        EmailVerificationService.VerifiedToken verified = emailVerificationService.verifyAndConsume(token);

        User user = userRepository.findById(verified.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            user.setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(user);
        }
    }

    private String buildVerificationLink(String token) {
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String sep = verificationBaseUrl.contains("?") ? "&" : "?";
        return verificationBaseUrl + sep + "token=" + encoded;
    }
}
