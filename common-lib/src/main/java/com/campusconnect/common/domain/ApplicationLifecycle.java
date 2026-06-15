package com.campusconnect.common.domain;

import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.web.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single canonical definition of the {@link Application} state machine (Story 5.5, FR-16;
 * architecture §8) — mirroring {@link DriveLifecycle}. Every status change must be a legal edge here, so
 * applications only ever move through valid states.
 *
 * <pre>
 *   APPLIED        → UNDER_REVIEW | SHORTLISTED | REJECTED | WITHDRAWN
 *   UNDER_REVIEW   → SHORTLISTED | REJECTED | WITHDRAWN
 *   SHORTLISTED    → INTERVIEWING | SELECTED | REJECTED
 *   INTERVIEWING   → INTERVIEWING | SELECTED | REJECTED        (multi-round, "INTERVIEWING per rounds")
 *   SELECTED       → OFFER_RELEASED | REJECTED
 *   OFFER_RELEASED → OFFER_ACCEPTED | OFFER_DECLINED | OFFER_EXPIRED
 *   OFFER_ACCEPTED, OFFER_DECLINED, OFFER_EXPIRED, REJECTED, WITHDRAWN → (terminal)
 * </pre>
 *
 * <p>Story 5.5 enforces only the <b>withdraw</b> edge ({@link #requireWithdrawable}: legal only from the
 * pre-shortlist states {@code APPLIED}/{@code UNDER_REVIEW}). The review/interview/offer forward edges are
 * the architecture's interpretation and are <b>called</b> by the Epic 6–7 stories via
 * {@link #requireTransition}; those stories refine the edges in this one place. Pure (no Spring).
 *
 * <p><b>Story 6.2 refinement:</b> a direct {@code APPLIED → SHORTLISTED} edge was added because the
 * recruiter shortlists straight from the applicant list and no story produces {@code UNDER_REVIEW} (the
 * 6.1 list is read-only). {@code UNDER_REVIEW} and {@code UNDER_REVIEW → SHORTLISTED} remain for a future
 * "mark as reviewing" action. The withdraw guard is unaffected ({@code SHORTLISTED → WITHDRAWN} stays absent).
 */
public final class ApplicationLifecycle {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> LEGAL =
            new EnumMap<>(ApplicationStatus.class);

    static {
        LEGAL.put(ApplicationStatus.APPLIED, EnumSet.of(
                ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED,
                ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));
        LEGAL.put(ApplicationStatus.UNDER_REVIEW, EnumSet.of(
                ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));
        LEGAL.put(ApplicationStatus.SHORTLISTED, EnumSet.of(
                ApplicationStatus.INTERVIEWING, ApplicationStatus.SELECTED, ApplicationStatus.REJECTED));
        LEGAL.put(ApplicationStatus.INTERVIEWING, EnumSet.of(
                ApplicationStatus.INTERVIEWING, ApplicationStatus.SELECTED, ApplicationStatus.REJECTED));
        LEGAL.put(ApplicationStatus.SELECTED, EnumSet.of(
                ApplicationStatus.OFFER_RELEASED, ApplicationStatus.REJECTED));
        LEGAL.put(ApplicationStatus.OFFER_RELEASED, EnumSet.of(
                ApplicationStatus.OFFER_ACCEPTED, ApplicationStatus.OFFER_DECLINED, ApplicationStatus.OFFER_EXPIRED));
        LEGAL.put(ApplicationStatus.OFFER_ACCEPTED, EnumSet.noneOf(ApplicationStatus.class));
        LEGAL.put(ApplicationStatus.OFFER_DECLINED, EnumSet.noneOf(ApplicationStatus.class));
        LEGAL.put(ApplicationStatus.OFFER_EXPIRED, EnumSet.noneOf(ApplicationStatus.class));
        LEGAL.put(ApplicationStatus.REJECTED, EnumSet.noneOf(ApplicationStatus.class));
        LEGAL.put(ApplicationStatus.WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
    }

    private ApplicationLifecycle() {
    }

    /** Whether {@code from → to} is a legal application transition. */
    public static boolean canTransition(ApplicationStatus from, ApplicationStatus to) {
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }

    /** Enforces a legal transition, else 409 {@code ILLEGAL_STATE_TRANSITION} (the Epic 6–7 entry point). */
    public static void requireTransition(ApplicationStatus from, ApplicationStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot move an application from " + from + " to " + to + ".");
        }
    }

    /**
     * The withdraw guard (Story 5.5): withdraw is legal only pre-shortlist ({@code APPLIED}/
     * {@code UNDER_REVIEW}). Once shortlisted or beyond — or already terminal — it is blocked with a
     * withdraw-specific 409 {@code WITHDRAW_NOT_ALLOWED}.
     */
    public static void requireWithdrawable(ApplicationStatus from) {
        if (!canTransition(from, ApplicationStatus.WITHDRAWN)) {
            throw new BusinessException(ErrorCode.WITHDRAW_NOT_ALLOWED,
                    "This application can no longer be withdrawn (status: " + from + ").");
        }
    }
}
