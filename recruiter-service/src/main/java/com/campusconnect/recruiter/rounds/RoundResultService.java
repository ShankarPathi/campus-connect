package com.campusconnect.recruiter.rounds;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationLifecycle;
import com.campusconnect.common.domain.ApplicationRound;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.campusconnect.recruiter.rounds.RecordResultsRequest.ResultEntry;
import com.campusconnect.recruiter.rounds.RoundResultsResponse.FailedItem;

/**
 * Record per-student results for an interview round and advance/reject accordingly (Story 6.4, FR-21).
 *
 * <p>Ownership is the gate (reused from 6.1–6.3): the drive is loaded owner-scoped and 404s otherwise.
 * Recording is resilient per-item (the 6.2 contract): a missing / already-decided / illegal / conflicted
 * entry lands in {@code failed} with a reason while the rest are recorded. A <b>PASS</b> sets the row to
 * {@code PASS} and, if a next round exists, creates the student's next-round {@code PENDING} row (the
 * application stays {@code INTERVIEWING} — the §8 self-loop; the {@code requireTransition} call is a guard
 * that the student wasn't concurrently rejected). Passing the <b>final</b> round leaves the application
 * {@code INTERVIEWING} for Story 6.5's explicit selection. A <b>FAIL/ABSENT</b> transitions the application
 * {@code INTERVIEWING → REJECTED} (the student notification is deferred to Epic 8 — see deferred-work.md).
 */
@Service
public class RoundResultService {

    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationRoundRepository applicationRoundRepository;
    private final AuditService auditService;

    public RoundResultService(DriveRepository driveRepository,
                              ApplicationRepository applicationRepository,
                              ApplicationRoundRepository applicationRoundRepository,
                              AuditService auditService) {
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.applicationRoundRepository = applicationRoundRepository;
        this.auditService = auditService;
    }

    public RoundResultsResponse recordResults(String driveId, int roundOrder, List<ResultEntry> entries) {
        Drive drive = requireMyDrive(driveId);
        if (drive.getRounds().stream().noneMatch(r -> r.getRoundOrder() == roundOrder)) {
            throw new ResourceNotFoundException("Round not found");
        }
        boolean hasNextRound = roundOrder < drive.getRounds().size();

        List<String> succeeded = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ResultEntry entry : entries) {
            String appId = entry.applicationId();
            if (!seen.add(appId)) {
                continue; // de-dup: first occurrence wins
            }
            RoundResult result = entry.result();
            if (result == RoundResult.PENDING) {
                failed.add(new FailedItem(appId, "result must be PASS, FAIL or ABSENT"));
                continue;
            }
            ApplicationRound row = applicationRoundRepository
                    .findByDriveIdAndRoundOrderAndApplicationId(driveId, roundOrder, appId).orElse(null);
            if (row == null) {
                failed.add(new FailedItem(appId, "Applicant is not in this round"));
                continue;
            }
            if (row.getResult() != RoundResult.PENDING) {
                failed.add(new FailedItem(appId, "Result already recorded"));
                continue;
            }
            Application app = applicationRepository.findById(appId).orElse(null);
            if (app == null) {
                failed.add(new FailedItem(appId, "Application not found"));
                continue;
            }
            try {
                if (result == RoundResult.PASS) {
                    // Guard the §8 self-loop: the student must still be interviewing (a concurrent reject throws).
                    ApplicationLifecycle.requireTransition(app.getStatus(), ApplicationStatus.INTERVIEWING);
                    if (hasNextRound
                            && !applicationRoundRepository.existsByApplicationIdAndRoundOrder(appId, roundOrder + 1)) {
                        ApplicationRound next = new ApplicationRound();
                        next.setApplicationId(appId);
                        next.setDriveId(driveId);
                        next.setRoundOrder(roundOrder + 1);
                        next.setResult(RoundResult.PENDING);
                        applicationRoundRepository.save(next);
                    }
                } else { // FAIL or ABSENT → reject
                    ApplicationLifecycle.requireTransition(app.getStatus(), ApplicationStatus.REJECTED);
                    app.setStatus(ApplicationStatus.REJECTED);
                    applicationRepository.save(app);
                }
            } catch (BusinessException illegal) {
                failed.add(new FailedItem(appId, illegal.getMessage()));
                continue;
            } catch (DuplicateKeyException dup) {
                // Concurrent double-record raced past the next-round exists-check into the unique index.
                failed.add(new FailedItem(appId, "Recorded concurrently; please retry."));
                continue;
            } catch (OptimisticLockingFailureException conflict) {
                failed.add(new FailedItem(appId, "The applicant changed concurrently; please retry."));
                continue;
            }

            // Record the outcome on the student's round row only after the advance/reject succeeded.
            row.setResult(result);
            applicationRoundRepository.save(row);
            auditService.record(AuditAction.ROUND_RESULT_RECORDED, "Application", appId,
                    "round=" + roundOrder + " result=PENDING", "round=" + roundOrder + " result=" + result);
            succeeded.add(appId);
        }
        return RoundResultsResponse.of(succeeded, failed);
    }

    /** Asserts the drive is the caller's own (tenant + owner scoped); 404 otherwise (the 6.1–6.3 gate). */
    private Drive requireMyDrive(String driveId) {
        return driveRepository.findByIdAndCreatedBy(driveId, TenantContext.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found"));
    }
}
