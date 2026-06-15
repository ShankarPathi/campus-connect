package com.campusconnect.recruiter.rounds;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationLifecycle;
import com.campusconnect.common.domain.ApplicationRound;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.InterviewRound;
import com.campusconnect.common.domain.RoundResult;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.ApplicationRoundRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.campusconnect.recruiter.rounds.RoundsResponse.RoundResponse;

/**
 * Define and sequence a drive's interview rounds, and enroll the shortlisted cohort into round 1 (Story 6.3,
 * FR-20). The recruiter's structural setup for multi-round interviewing.
 *
 * <p>Ownership is the gate (reused from 6.1/6.2): every entry point loads the drive owner-scoped and 404s
 * otherwise. The round definitions live embedded on {@link Drive}'s {@code rounds[]}; the per-student
 * instances are lean {@link ApplicationRound} rows (referencing the round by order, carrying only
 * {@code result}). Defining rounds transitions every {@code SHORTLISTED} applicant to {@code INTERVIEWING}
 * (via the canonical {@link ApplicationLifecycle}) and creates their round-1 row — idempotently, so a re-PUT
 * after shortlisting more students simply enrolls the newcomers. The reschedule notification is deferred to
 * Epic 8 (see deferred-work.md).
 */
@Service
public class RoundService {

    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationRoundRepository applicationRoundRepository;
    private final AuditService auditService;

    public RoundService(DriveRepository driveRepository,
                        ApplicationRepository applicationRepository,
                        ApplicationRoundRepository applicationRoundRepository,
                        AuditService auditService) {
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.applicationRoundRepository = applicationRoundRepository;
        this.auditService = auditService;
    }

    /** Define/replace the round sequence and enroll the shortlisted cohort into round 1 (idempotent). */
    public RoundsResponse defineRounds(String driveId, DefineRoundsRequest request) {
        Drive drive = requireMyDrive(driveId);

        List<InterviewRound> incoming = toRounds(request.rounds());

        // Definition-freeze guard (AC7): once any round has started (a non-PENDING result exists), the whole
        // round definition is frozen — count / name / mode / order AND schedule / venue. A same-definition
        // re-PUT (only re-running the idempotent round-1 assignment) is always allowed. Freezing the schedule
        // too is what stops a PUT from silently bypassing the reschedule "before it occurs" guard + audit +
        // notify (a started round's time must change through PATCH /reschedule, which has its own guards).
        boolean anyResult = applicationRoundRepository.findByDriveId(driveId).stream()
                .anyMatch(ar -> ar.getResult() != RoundResult.PENDING);
        if (anyResult && definitionChanged(drive.getRounds(), incoming)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Interview rounds cannot be changed once a round has started; reschedule a future round instead.");
        }

        int oldCount = drive.getRounds().size();
        drive.setRounds(incoming);
        driveRepository.save(drive);

        assignShortlistedToRoundOne(driveId);

        auditService.record(AuditAction.INTERVIEW_ROUNDS_DEFINED, "Drive", driveId,
                "rounds=" + oldCount, "rounds=" + incoming.size());

        return buildResponse(drive);
    }

    /** The drive's defined rounds with per-round assigned counts. */
    public RoundsResponse getRounds(String driveId) {
        return buildResponse(requireMyDrive(driveId));
    }

    /** Reschedule a not-yet-occurred round (schedule/venue) and notify assigned students (notify → Epic 8). */
    public RoundsResponse reschedule(String driveId, int roundOrder, RescheduleRoundRequest request) {
        Drive drive = requireMyDrive(driveId);
        InterviewRound round = drive.getRounds().stream()
                .filter(r -> r.getRoundOrder() == roundOrder)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Round not found"));

        // "before it occurs": the round's current schedule must still be in the future...
        if (round.getSchedule() == null || !round.getSchedule().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "A round that has already occurred cannot be rescheduled.");
        }
        // ...and no result may have been recorded for it yet.
        boolean started = applicationRoundRepository.findByDriveIdAndRoundOrder(driveId, roundOrder).stream()
                .anyMatch(ar -> ar.getResult() != RoundResult.PENDING);
        if (started) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "A round with recorded results cannot be rescheduled.");
        }

        Instant oldSchedule = round.getSchedule();
        round.setSchedule(request.schedule());
        if (request.venueOrLink() != null && !request.venueOrLink().isBlank()) {
            round.setVenueOrLink(request.venueOrLink());
        }
        driveRepository.save(drive);

        auditService.record(AuditAction.ROUND_RESCHEDULED, "Drive", driveId,
                "round=" + roundOrder + " schedule=" + oldSchedule, "schedule=" + request.schedule());
        // NOTE: notifying the round's assigned students is deferred to Epic 8 (see deferred-work.md).
        return buildResponse(drive);
    }

    // ── internals ──

    /** Transition every SHORTLISTED applicant → INTERVIEWING and create their PENDING round-1 row (idempotent). */
    private void assignShortlistedToRoundOne(String driveId) {
        for (Application app : applicationRepository.findByDriveIdAndStatusIn(
                driveId, List.of(ApplicationStatus.SHORTLISTED))) {
            ApplicationLifecycle.requireTransition(app.getStatus(), ApplicationStatus.INTERVIEWING);
            app.setStatus(ApplicationStatus.INTERVIEWING);
            applicationRepository.save(app);
            if (!applicationRoundRepository.existsByApplicationIdAndRoundOrder(app.getId(), 1)) {
                ApplicationRound row = new ApplicationRound();
                row.setApplicationId(app.getId());
                row.setDriveId(driveId);
                row.setRoundOrder(1);
                row.setResult(RoundResult.PENDING);
                applicationRoundRepository.save(row);
            }
        }
    }

    private static List<InterviewRound> toRounds(List<DefineRoundsRequest.RoundSpec> specs) {
        List<InterviewRound> rounds = new ArrayList<>();
        int order = 1;
        for (DefineRoundsRequest.RoundSpec spec : specs) {
            InterviewRound r = new InterviewRound();
            r.setRoundOrder(order++);
            r.setName(spec.name());
            r.setMode(spec.mode());
            r.setSchedule(spec.schedule());
            r.setVenueOrLink(spec.venueOrLink());
            rounds.add(r);
        }
        return rounds;
    }

    /**
     * Whether the definition differs in any field — count, or any position's order / name / mode / schedule /
     * venue. Schedule and venue are included (unlike a pure "structure" compare) so that once a round has
     * started, a PUT cannot change a started round's time/venue behind the reschedule guard's back.
     */
    private static boolean definitionChanged(List<InterviewRound> existing, List<InterviewRound> incoming) {
        if (existing.size() != incoming.size()) {
            return true;
        }
        for (int i = 0; i < existing.size(); i++) {
            InterviewRound a = existing.get(i);
            InterviewRound b = incoming.get(i);
            if (a.getRoundOrder() != b.getRoundOrder()
                    || !Objects.equals(a.getName(), b.getName())
                    || a.getMode() != b.getMode()
                    || !Objects.equals(a.getSchedule(), b.getSchedule())
                    || !Objects.equals(a.getVenueOrLink(), b.getVenueOrLink())) {
                return true;
            }
        }
        return false;
    }

    private RoundsResponse buildResponse(Drive drive) {
        List<RoundResponse> rounds = drive.getRounds().stream()
                .map(r -> new RoundResponse(r.getRoundOrder(), r.getName(), r.getMode(), r.getSchedule(),
                        r.getVenueOrLink(),
                        applicationRoundRepository.countByDriveIdAndRoundOrder(drive.getId(), r.getRoundOrder())))
                .toList();
        return new RoundsResponse(rounds);
    }

    private Drive requireMyDrive(String driveId) {
        return driveRepository.findByIdAndCreatedBy(driveId, TenantContext.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found"));
    }
}
