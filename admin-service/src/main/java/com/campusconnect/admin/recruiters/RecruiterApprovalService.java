package com.campusconnect.admin.recruiters;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.RecruiterProfileRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * College-Admin approval gate for recruiters (Story 2.2, FR-4). Every operation is scoped to the
 * calling admin's tenant via {@link TenantContext}: a recruiter in another tenant is simply not found
 * (404), which is the cross-tenant isolation guard — there is no separate "is this mine?" branch to forget.
 * admin-service owns recruiter approvals per architecture §3.
 */
@Service
public class RecruiterApprovalService {

    private static final Logger log = LoggerFactory.getLogger(RecruiterApprovalService.class);

    private final UserRepository userRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final EmailService emailService;

    public RecruiterApprovalService(
            UserRepository userRepository,
            RecruiterProfileRepository recruiterProfileRepository,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.emailService = emailService;
    }

    /** Recruiters of the admin's tenant in the given status, joined with their company details. */
    public List<PendingRecruiterResponse> listByStatus(AccountStatus status) {
        String tenantId = TenantContext.requireTenantId();
        List<User> users = userRepository.findByTenantIdAndRoleAndAccountStatus(tenantId, Role.RECRUITER, status);
        if (users.isEmpty()) {
            return List.of();
        }
        Map<String, RecruiterProfile> byUser = recruiterProfileRepository
                .findByTenantIdAndUserIdIn(tenantId, users.stream().map(User::getId).toList())
                .stream()
                .collect(Collectors.toMap(RecruiterProfile::getUserId, Function.identity()));
        return users.stream()
                .map(u -> PendingRecruiterResponse.of(u, byUser.get(u.getId())))
                .toList();
    }

    /** Approve a PENDING_APPROVAL recruiter in the admin's tenant → ACTIVE, and notify them. */
    public void approve(String userId) {
        User recruiter = loadPendingRecruiter(userId);
        recruiter.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(recruiter);
        notify(recruiter.getEmail(),
                "Your Campus Connect recruiter account is approved",
                """
                Good news — your recruiter account has been approved by the college.
                You can now log in to Campus Connect and start posting drives.
                """);
    }

    /** Reject a PENDING_APPROVAL recruiter in the admin's tenant → REJECTED + reason, and notify them. */
    public void reject(String userId, String reason) {
        User recruiter = loadPendingRecruiter(userId);
        recruiter.setAccountStatus(AccountStatus.REJECTED);
        recruiter.setRejectionReason(reason);
        userRepository.save(recruiter);
        notify(recruiter.getEmail(),
                "Your Campus Connect recruiter account was not approved",
                """
                Your recruiter account was not approved by the college.

                Reason: %s
                """.formatted(reason));
    }

    /**
     * Best-effort notification: the decision (the status change) is already committed and authoritative,
     * so a transient SMTP failure must not fail the request or it would leave the admin unable to retry
     * (the status is no longer PENDING_APPROVAL → 409). Epic 8 replaces this with the durable, retrying
     * email outbox.
     */
    private void notify(String to, String subject, String body) {
        try {
            emailService.sendEmail(to, subject, body);
        } catch (RuntimeException ex) {
            log.warn("Failed to send recruiter decision notification to {} (decision stands)", to, ex);
        }
    }

    /**
     * Loads a recruiter that belongs to the admin's tenant and is awaiting a decision. The
     * tenant-scoped profile lookup is the isolation guard (404 for another tenant's recruiter); the
     * status guard forbids deciding on an account that is not PENDING_APPROVAL.
     */
    private User loadPendingRecruiter(String userId) {
        String tenantId = TenantContext.requireTenantId();
        // isolation: a recruiter outside this admin's tenant has no profile here → not found
        recruiterProfileRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter not found"));

        User recruiter = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter not found"));
        if (recruiter.getRole() != Role.RECRUITER || !tenantId.equals(recruiter.getTenantId())) {
            throw new ResourceNotFoundException("Recruiter not found");
        }
        if (recruiter.getAccountStatus() != AccountStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Recruiter is not awaiting approval (status: " + recruiter.getAccountStatus() + ")");
        }
        return recruiter;
    }
}
