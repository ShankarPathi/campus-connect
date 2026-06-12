package com.campusconnect.recruiter.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.email.EmailVerificationService;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.DuplicateResourceException;
import com.campusconnect.common.exception.ForbiddenException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.RecruiterProfileRepository;
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
 * Recruiter self-registration and email verification (Story 2.2, FR-4). Mirrors
 * {@code StudentRegistrationService}: resolve tenant from {@code collegeCode}, normalize email,
 * 404-before-409, BCrypt, send-failure rollback. Differences: it also persists a {@link RecruiterProfile}
 * with the company details, and verification parks the account at {@code PENDING_APPROVAL} (awaiting a
 * College Admin) rather than {@code ACTIVE}.
 */
@Service
public class RecruiterRegistrationService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;
    private final String verificationBaseUrl;

    public RecruiterRegistrationService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RecruiterProfileRepository recruiterProfileRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            EmailService emailService,
            @Value("${app.verification.base-url}") String verificationBaseUrl) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.emailService = emailService;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    /**
     * Register a recruiter under {@code collegeCode}: creates an inactive (PENDING_VERIFICATION) account
     * plus a RecruiterProfile with the company details, issues a verification token, and emails the link.
     * On send failure, rolls back the profile + user + token so the address is not orphaned.
     */
    public RecruiterRegistrationResponse register(RegisterRecruiterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

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
        user.setRole(Role.RECRUITER);
        user.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        User saved = userRepository.save(user);

        // Everything after the User insert is rolled back on ANY failure (profile save, token issue, or
        // email send) so a partial registration never orphans the User and locks the address out (409).
        try {
            RecruiterProfile profile = new RecruiterProfile();
            profile.setTenantId(tenant.getId());
            profile.setUserId(saved.getId());
            profile.setCompanyName(request.companyName().trim());
            profile.setCompanyWebsite(request.companyWebsite());
            profile.setIndustry(request.industry());
            profile.setCompanyDescription(request.companyDescription());
            profile.setRecruiterDesignation(request.recruiterDesignation());
            profile.setContactPhone(request.contactPhone());
            recruiterProfileRepository.save(profile);

            String token = emailVerificationService.issueToken(saved.getId(), tenant.getId());
            emailService.sendVerificationEmail(email, buildVerificationLink(token));
        } catch (RuntimeException ex) {
            emailVerificationService.discardTokens(saved.getId());
            recruiterProfileRepository.deleteByUserIdAndTenantId(saved.getId(), tenant.getId());
            userRepository.deleteById(saved.getId());
            if (ex instanceof BusinessException be) {
                throw be; // preserve a specific code (e.g. a duplicate-profile conflict)
            }
            throw new BusinessException(
                    ErrorCode.EMAIL_SEND_FAILED, "Could not complete registration — please try again");
        }

        return RecruiterRegistrationResponse.from(saved);
    }

    /** Verify an email token and move the recruiter to PENDING_APPROVAL (awaiting College Admin). */
    public void verifyEmail(String token) {
        EmailVerificationService.VerifiedToken verified = emailVerificationService.verifyAndConsume(token);

        User user = userRepository.findById(verified.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // only advance from the pre-approval state; never downgrade an already-approved/rejected account
        if (user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            user.setAccountStatus(AccountStatus.PENDING_APPROVAL);
            userRepository.save(user);
        }
    }

    private String buildVerificationLink(String token) {
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String sep = verificationBaseUrl.contains("?") ? "&" : "?";
        return verificationBaseUrl + sep + "token=" + encoded;
    }
}
