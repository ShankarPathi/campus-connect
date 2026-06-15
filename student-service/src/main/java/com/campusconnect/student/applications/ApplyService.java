package com.campusconnect.student.applications;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.PlacementPolicy;
import com.campusconnect.common.domain.Resume;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.eligibility.EligibilityContext;
import com.campusconnect.common.eligibility.EligibilityEngine;
import com.campusconnect.common.eligibility.EligibilityResult;
import com.campusconnect.common.eligibility.PolicyResolver;
import com.campusconnect.common.eligibility.ResolvedPolicy;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.repository.ResumeRepository;
import com.campusconnect.common.repository.StudentProfileRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * The apply transaction (Story 5.4, FR-15) — the second live call site of the eligibility engine (the
 * short-circuit {@code check} gate; Story 5.3 used {@code checkAll}). A passing apply freezes the
 * student's active résumé onto a new {@link Application} ({@code APPLIED}).
 *
 * <p><b>Idempotent:</b> a duplicate is rejected with {@code DUPLICATE_APPLICATION} (409) — a pre-check
 * for the common case and the unique {@code {tenantId, studentId, driveId}} index as the concurrency
 * backstop (a racing double-submit fails at the DB → caught here), so no second row is ever created.
 */
@Service
public class ApplyService {

    private static final Logger log = LoggerFactory.getLogger(ApplyService.class);

    private final DriveRepository driveRepository;
    private final StudentProfileRepository profileRepository;
    private final ApplicationRepository applicationRepository;
    private final ResumeRepository resumeRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public ApplyService(DriveRepository driveRepository, StudentProfileRepository profileRepository,
                        ApplicationRepository applicationRepository, ResumeRepository resumeRepository,
                        TenantRepository tenantRepository, UserRepository userRepository,
                        EmailService emailService) {
        this.driveRepository = driveRepository;
        this.profileRepository = profileRepository;
        this.applicationRepository = applicationRepository;
        this.resumeRepository = resumeRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public ApplicationResponse apply(String driveId) {
        String studentId = TenantContext.getUserId();

        // Tenant-scoped load — a missing or cross-tenant drive is simply not found.
        Drive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Drive not found."));

        // Duplicate pre-check (the friendly common case); the unique index is the race backstop below.
        if (applicationRepository.existsByStudentIdAndDriveId(studentId, driveId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_APPLICATION, "You have already applied to this drive.");
        }

        // Eligibility gate — the full context, short-circuit check(). alreadyApplied is false: a duplicate
        // is handled out-of-band as a 409 above/below, not as the engine's rule-5 400.
        StudentProfile profile = profileRepository.findByStudentId(studentId).orElse(null);
        PlacementPolicy tenantPolicy = tenantRepository.findById(TenantContext.requireTenantId())
                .map(Tenant::getPlacementPolicy)
                .orElse(null);
        Instant now = Instant.now();
        ResolvedPolicy resolved = PolicyResolver.resolve(tenantPolicy, drive.getEligibility());
        EligibilityResult result = EligibilityEngine.check(
                new EligibilityContext(AccountStatus.ACTIVE, profile, drive, resolved, false, now));
        if (!result.eligible()) {
            throw new BusinessException(ErrorCode.NOT_ELIGIBLE, result.reason());
        }

        // Résumé snapshot — freeze the active résumé's key. No active résumé ⇒ nothing to snapshot.
        Resume active = resumeRepository.findActiveByUserId(studentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_ERROR, "An active résumé is required to apply."));

        Application application = new Application();
        application.setStudentId(studentId);
        application.setDriveId(driveId);
        application.setStatus(ApplicationStatus.APPLIED);
        application.setAppliedAt(now);
        application.setResumeSnapshotKey(active.getS3Key());

        Application saved;
        try {
            saved = applicationRepository.save(application);
        } catch (DuplicateKeyException ex) {
            // A concurrent double-submit raced past the pre-check; the unique index rejected the second
            // write — no second row exists. Surface the same idempotent 409.
            throw new BusinessException(ErrorCode.DUPLICATE_APPLICATION, "You have already applied to this drive.");
        }

        sendConfirmation(studentId, drive);
        return ApplicationResponse.of(saved, drive);
    }

    /**
     * Best-effort confirmation email (the Story 4.3 {@code notifyRecruiter} pattern): the apply is already
     * committed and authoritative, so a transient SMTP failure must not fail it — a retry would 409. The
     * durable in-app notification + retrying outbox are Epic 8 (architecture §9).
     */
    private void sendConfirmation(String studentId, Drive drive) {
        try {
            userRepository.findById(studentId).ifPresent(u -> emailService.sendEmail(
                    u.getEmail(),
                    "Application submitted — " + safe(drive.getCompanyName()),
                    "You have successfully applied to the %s role at %s. You can track it in My Applications."
                            .formatted(safe(drive.getRole()), safe(drive.getCompanyName()))));
        } catch (RuntimeException ex) {
            log.warn("Failed to send apply confirmation for student {} drive {} (apply stands)",
                    studentId, drive.getId(), ex);
        }
    }

    private static String safe(String s) {
        return s != null ? s : "(unspecified)";
    }
}
