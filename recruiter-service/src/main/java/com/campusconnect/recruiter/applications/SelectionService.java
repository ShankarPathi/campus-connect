package com.campusconnect.recruiter.applications;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationLifecycle;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.RoundResult;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.ApplicationRoundRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.tenancy.TenantContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.campusconnect.recruiter.applications.SelectionResponse.FailedItem;

/**
 * Mark the students who cleared every interview round as {@code SELECTED} (Story 6.5, FR-22) — the
 * culmination of the funnel and the unlock for Epic 7's offers.
 *
 * <p>Ownership is the gate (reused from 6.1–6.4): the drive is loaded owner-scoped and 404s otherwise.
 * Selection is resilient per-item (the 6.2 contract). A student is selectable only if they are
 * {@code INTERVIEWING} and have a {@code PASS} on the drive's <b>final</b> round (the AC's "passed the final
 * round"); a mid-interview / rejected / no-rounds applicant lands in {@code failed}. The transition
 * {@code INTERVIEWING → SELECTED} goes through the canonical {@link ApplicationLifecycle}, is audited, and is
 * {@code @Version}-safe. Openings is a <b>soft warning</b> in the response, never a block — a recruiter may
 * over-select. {@code SELECTED} itself is the offer unlock; no offer code lives here.
 */
@Service
public class SelectionService {

    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationRoundRepository applicationRoundRepository;
    private final AuditService auditService;

    public SelectionService(DriveRepository driveRepository,
                            ApplicationRepository applicationRepository,
                            ApplicationRoundRepository applicationRoundRepository,
                            AuditService auditService) {
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.applicationRoundRepository = applicationRoundRepository;
        this.auditService = auditService;
    }

    public SelectionResponse select(String driveId, List<String> applicationIds) {
        Drive drive = requireMyDrive(driveId);
        int finalRound = drive.getRounds().size(); // contiguous 1..N (Story 6.3); 0 → no rounds → nobody selectable

        List<String> succeeded = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String id : applicationIds) {
            if (!seen.add(id)) {
                continue; // de-dup: first occurrence wins
            }
            Application app = applicationRepository.findByIdAndDriveId(id, driveId).orElse(null);
            if (app == null) {
                failed.add(new FailedItem(id, "Applicant not found"));
                continue;
            }
            if (!passedFinalRound(driveId, finalRound, id)) {
                failed.add(new FailedItem(id, "Applicant has not passed the final round"));
                continue;
            }
            ApplicationStatus from = app.getStatus();
            try {
                ApplicationLifecycle.requireTransition(from, ApplicationStatus.SELECTED);
                app.setStatus(ApplicationStatus.SELECTED);
                applicationRepository.save(app);
            } catch (BusinessException illegal) {
                failed.add(new FailedItem(id, illegal.getMessage()));
                continue;
            } catch (OptimisticLockingFailureException conflict) {
                failed.add(new FailedItem(id, "The applicant changed concurrently; please retry."));
                continue;
            }
            auditService.record(AuditAction.APPLICANT_SELECTED, "Application", id,
                    "status=" + from, "status=" + ApplicationStatus.SELECTED);
            succeeded.add(id);
        }

        int selectedTotal = applicationRepository
                .findByDriveIdAndStatusIn(driveId, List.of(ApplicationStatus.SELECTED)).size();
        return SelectionResponse.of(succeeded, failed, selectedTotal, drive.getOpenings());
    }

    /** Whether the applicant has a {@code PASS} on the drive's final round (and the drive has rounds at all). */
    private boolean passedFinalRound(String driveId, int finalRound, String applicationId) {
        return finalRound >= 1 && applicationRoundRepository
                .findByDriveIdAndRoundOrderAndApplicationId(driveId, finalRound, applicationId)
                .map(r -> r.getResult() == RoundResult.PASS)
                .orElse(false);
    }

    /** Asserts the drive is the caller's own (tenant + owner scoped); 404 otherwise (the 6.1–6.4 gate). */
    private Drive requireMyDrive(String driveId) {
        return driveRepository.findByIdAndCreatedBy(driveId, TenantContext.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found"));
    }
}
