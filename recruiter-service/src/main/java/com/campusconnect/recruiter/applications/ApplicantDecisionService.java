package com.campusconnect.recruiter.applications;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationLifecycle;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.tenancy.TenantContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import com.campusconnect.recruiter.applications.BulkDecisionResponse.FailedItem;

/**
 * Shortlist or reject applicants to a recruiter's own drive (Story 6.2, FR-19) — the recruiter's first
 * <b>write</b> on an application.
 *
 * <p>Ownership is the gate (reused from Story 6.1): the drive is loaded owner-scoped ({@code createdBy} =
 * {@link TenantContext#getUserId()}, tenant-scoped via {@link DriveRepository}) and 404s otherwise, before
 * any item is touched. Each transition goes through the canonical {@link ApplicationLifecycle} (no loose
 * status writes), is audited, and saves under the {@code @Version} optimistic lock.
 *
 * <p><b>Resilient per-item bulk (Decision C):</b> a bulk action runs over a pool whose states may have
 * changed, so it never fails wholesale — a missing/illegal/conflicted item lands in {@code failed} with a
 * reason while the rest apply. The student notification (the FR-19 "the student is notified" half) is
 * deferred to Epic 8's {@code EventPublisher}/outbox (see deferred-work.md), as at Stories 4.3/4.4.
 */
@Service
public class ApplicantDecisionService {

    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditService auditService;

    public ApplicantDecisionService(DriveRepository driveRepository,
                                    ApplicationRepository applicationRepository,
                                    AuditService auditService) {
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.auditService = auditService;
    }

    /** Advance each applicant {@code → SHORTLISTED} (legal from APPLIED/UNDER_REVIEW). */
    public BulkDecisionResponse shortlist(String driveId, List<String> applicationIds) {
        return decide(driveId, applicationIds, ApplicationStatus.SHORTLISTED, AuditAction.APPLICANT_SHORTLISTED);
    }

    /** Advance each applicant {@code → REJECTED} (legal from any active, pre-terminal state). */
    public BulkDecisionResponse reject(String driveId, List<String> applicationIds) {
        return decide(driveId, applicationIds, ApplicationStatus.REJECTED, AuditAction.APPLICANT_REJECTED);
    }

    private BulkDecisionResponse decide(String driveId, List<String> applicationIds,
                                        ApplicationStatus target, AuditAction auditAction) {
        requireMyDrive(driveId);

        List<String> succeeded = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();

        for (String id : applicationIds.stream().distinct().toList()) {
            Application app = applicationRepository.findByIdAndDriveId(id, driveId).orElse(null);
            if (app == null) {
                failed.add(new FailedItem(id, "Applicant not found"));
                continue;
            }
            ApplicationStatus from = app.getStatus();
            try {
                ApplicationLifecycle.requireTransition(from, target);
            } catch (BusinessException illegal) {
                failed.add(new FailedItem(id, illegal.getMessage()));
                continue;
            }
            app.setStatus(target);
            try {
                applicationRepository.save(app);
            } catch (OptimisticLockingFailureException conflict) {
                failed.add(new FailedItem(id, "The applicant changed concurrently; please retry."));
                continue;
            }
            auditService.record(auditAction, "Application", id, "status=" + from, "status=" + target);
            succeeded.add(id);
        }
        return BulkDecisionResponse.of(succeeded, failed);
    }

    /** Asserts the drive is the caller's own (tenant + owner scoped); 404 otherwise (the Story 6.1 gate). */
    private void requireMyDrive(String driveId) {
        driveRepository.findByIdAndCreatedBy(driveId, TenantContext.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found"));
    }
}
