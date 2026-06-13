package com.campusconnect.admin.profiles;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.AcademicDetails;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.profile.ProfileCompletion;
import com.campusconnect.common.repository.StudentProfileRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * College-Admin approval/rejection/correction of student placement profiles (Story 3.3, FR-9). Every
 * operation is scoped to the calling admin's tenant via the tenant-aware {@link StudentProfileRepository}:
 * a profile in another tenant is simply not found (404) — the cross-tenant isolation guard, no separate
 * ownership branch. admin-service owns profile approvals (architecture §3). Decisions and edits are
 * written to the append-only audit trail; the student is notified best-effort (the 2.2 pattern).
 */
@Service
public class ProfileApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ProfileApprovalService.class);
    private static final String ENTITY_TYPE = "StudentProfile";

    private final StudentProfileRepository profileRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    public ProfileApprovalService(StudentProfileRepository profileRepository, TenantRepository tenantRepository,
                                  UserRepository userRepository, EmailService emailService, AuditService auditService) {
        this.profileRepository = profileRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    /** Profiles of the admin's tenant in the given approval status. */
    public List<PendingProfileResponse> listByStatus(ProfileApprovalStatus status) {
        return profileRepository.findByApprovalStatus(status).stream()
                .map(PendingProfileResponse::of)
                .toList();
    }

    /**
     * Season lock (Story 3.4): freeze every profile in the admin's tenant ({@code isLocked = true}),
     * independent of approval status — a frozen profile cannot be edited/submitted by the student even if
     * APPROVED. Idempotent (re-locking is a no-op for already-locked rows). Records one audit row with the
     * affected count and returns it. Does NOT touch {@code profileApprovalStatus} (approval ≠ lock).
     */
    public long lockSeason() {
        return setSeasonLock(true, AuditAction.PROFILE_LOCKED);
    }

    /** Season unlock (Story 3.4): clear {@code isLocked} on every profile in the tenant. Idempotent. */
    public long unlockSeason() {
        return setSeasonLock(false, AuditAction.PROFILE_UNLOCKED);
    }

    private long setSeasonLock(boolean locked, AuditAction action) {
        long affected = profileRepository.setLockedForTenant(locked);
        if (affected > 0) { // an empty tenant is a no-op — no security event to record
            auditService.record(action, ENTITY_TYPE, TenantContext.requireTenantId(),
                    null, "isLocked=" + locked + "; affected=" + affected);
        }
        return affected;
    }

    /** Approve a PENDING_APPROVAL profile in the admin's tenant → APPROVED; audit + notify. */
    public void approve(String studentId) {
        StudentProfile profile = loadPendingProfile(studentId);
        profile.setProfileApprovalStatus(ProfileApprovalStatus.APPROVED);
        profileRepository.save(profile);
        auditService.record(AuditAction.PROFILE_APPROVED, ENTITY_TYPE, profile.getId(),
                "status=PENDING_APPROVAL", "status=APPROVED");
        notifyStudent(studentId,
                "Your Campus Connect placement profile is approved",
                "Your placement profile has been approved. You can now apply to eligible drives.");
    }

    /** Reject a PENDING_APPROVAL profile in the admin's tenant → REJECTED + reason; audit + notify. */
    public void reject(String studentId, String reason) {
        StudentProfile profile = loadPendingProfile(studentId);
        profile.setProfileApprovalStatus(ProfileApprovalStatus.REJECTED);
        profile.setRejectionReason(reason);
        profileRepository.save(profile);
        auditService.record(AuditAction.PROFILE_REJECTED, ENTITY_TYPE, profile.getId(),
                "status=PENDING_APPROVAL", "status=REJECTED; reason=" + reason);
        notifyStudent(studentId,
                "Your Campus Connect placement profile was not approved",
                "Your placement profile was not approved.\n\nReason: %s\n\nYou can correct it and re-submit."
                        .formatted(reason));
    }

    /**
     * Admin correction of a profile's academic fields (e.g. CGPA) in the admin's tenant. Only the
     * provided fields are applied, validated against the tenant; approval status is unchanged. The
     * before/after of every field that actually changed is written to the audit trail.
     */
    public void editAcademics(String studentId, AdminEditProfileRequest request) {
        StudentProfile profile = loadProfile(studentId);
        Tenant tenant = tenantRepository.findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found."));

        AcademicDetails academic = profile.getAcademic();
        boolean branchChanges = request.branch() != null && !request.branch().equals(academic.getBranch());
        boolean batchChanges = request.batch() != null && !request.batch().equals(profile.getBatch());

        // Validate every changing field BEFORE mutating anything (validation gates mutation).
        if (branchChanges) {
            requireMember(tenant.getBranches(), request.branch(), "academic.branch", "branch");
        }
        if (batchChanges) {
            requireMember(tenant.getBatches(), request.batch(), "batch", "batch");
        }

        // Apply changed fields and record the before/after delta.
        List<String> oldParts = new ArrayList<>();
        List<String> newParts = new ArrayList<>();
        if (branchChanges) {
            oldParts.add("branch=" + academic.getBranch());
            newParts.add("branch=" + request.branch());
            academic.setBranch(request.branch());
        }
        if (request.cgpa() != null && !request.cgpa().equals(academic.getCgpa())) {
            oldParts.add("cgpa=" + academic.getCgpa());
            newParts.add("cgpa=" + request.cgpa());
            academic.setCgpa(request.cgpa());
        }
        if (request.activeBacklogs() != null && !request.activeBacklogs().equals(academic.getActiveBacklogs())) {
            oldParts.add("activeBacklogs=" + academic.getActiveBacklogs());
            newParts.add("activeBacklogs=" + request.activeBacklogs());
            academic.setActiveBacklogs(request.activeBacklogs());
        }
        if (batchChanges) {
            oldParts.add("batch=" + profile.getBatch());
            newParts.add("batch=" + request.batch());
            profile.setBatch(request.batch());
        }

        if (oldParts.isEmpty()) {
            return; // nothing changed — no write, no audit row
        }

        profile.setCompletionPercent(ProfileCompletion.percentOf(profile));
        profileRepository.save(profile);
        auditService.record(AuditAction.PROFILE_EDITED, ENTITY_TYPE, profile.getId(),
                String.join(", ", oldParts), String.join(", ", newParts));
    }

    // ── internals ──

    private void requireMember(List<String> allowed, String value, String field, String label) {
        if (!allowed.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Unknown " + label + " for this college.", Map.of(field, "unknown " + label + ": " + value));
        }
    }

    /** Tenant-scoped profile (404 for another tenant / missing) — the isolation guard. */
    private StudentProfile loadProfile(String studentId) {
        return profileRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    /** As {@link #loadProfile} plus the PENDING_APPROVAL state guard for a decision. */
    private StudentProfile loadPendingProfile(String studentId) {
        StudentProfile profile = loadProfile(studentId);
        if (profile.getProfileApprovalStatus() != ProfileApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Profile is not awaiting approval (status: " + profile.getProfileApprovalStatus() + ")");
        }
        return profile;
    }

    /**
     * Best-effort notification (the 2.2 pattern): the decision is already committed and authoritative, so
     * a transient SMTP failure must not fail the request (the status is no longer PENDING_APPROVAL → a
     * retry would 409). Epic 8 replaces this with the durable email outbox.
     */
    private void notifyStudent(String studentId, String subject, String body) {
        try {
            userRepository.findById(studentId).ifPresent(u -> emailService.sendEmail(u.getEmail(), subject, body));
        } catch (RuntimeException ex) {
            log.warn("Failed to send profile decision notification to student {} (decision stands)", studentId, ex);
        }
    }
}
