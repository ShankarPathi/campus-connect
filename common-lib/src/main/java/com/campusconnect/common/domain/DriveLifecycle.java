package com.campusconnect.common.domain;

import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.web.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single canonical definition of the drive state machine (Story 4.4, FR-11; architecture §8). Every
 * status change must be a legal edge here — {@link #requireTransition} is the one enforcement point, so
 * drives only ever move through valid states.
 *
 * <pre>
 *   DRAFT             → PENDING_APPROVAL                         (recruiter submit, 4.2)
 *   PENDING_APPROVAL  → PUBLISHED | REJECTED_BY_ADMIN | CANCELLED (admin approve/reject 4.3, recruiter cancel 4.4)
 *   REJECTED_BY_ADMIN → PENDING_APPROVAL                         (recruiter fix-and-resubmit, 4.3)
 *   PUBLISHED         → ONGOING | CANCELLED                      (drive-deadline job [Epic 10], recruiter cancel)
 *   ONGOING           → CLOSED | CANCELLED                       (job [Epic 10], recruiter cancel)
 *   CLOSED            → COMPLETED                                (drive-completion job [Epic 10])
 *   COMPLETED, CANCELLED → (terminal)
 * </pre>
 *
 * <p>The existing 4.2/4.3 guards (recruiter {@code submit}, admin {@code approve}/{@code reject}) encode
 * exactly these edges; this table is their canonical reference and the authority the Epic-10 scheduled
 * jobs call for the forward edges. Pure (no Spring).
 */
public final class DriveLifecycle {

    private static final Map<DriveStatus, Set<DriveStatus>> LEGAL = new EnumMap<>(DriveStatus.class);

    static {
        LEGAL.put(DriveStatus.DRAFT, EnumSet.of(DriveStatus.PENDING_APPROVAL));
        LEGAL.put(DriveStatus.PENDING_APPROVAL,
                EnumSet.of(DriveStatus.PUBLISHED, DriveStatus.REJECTED_BY_ADMIN, DriveStatus.CANCELLED));
        LEGAL.put(DriveStatus.REJECTED_BY_ADMIN, EnumSet.of(DriveStatus.PENDING_APPROVAL));
        LEGAL.put(DriveStatus.PUBLISHED, EnumSet.of(DriveStatus.ONGOING, DriveStatus.CANCELLED));
        LEGAL.put(DriveStatus.ONGOING, EnumSet.of(DriveStatus.CLOSED, DriveStatus.CANCELLED));
        LEGAL.put(DriveStatus.CLOSED, EnumSet.of(DriveStatus.COMPLETED));
        LEGAL.put(DriveStatus.COMPLETED, EnumSet.noneOf(DriveStatus.class));
        LEGAL.put(DriveStatus.CANCELLED, EnumSet.noneOf(DriveStatus.class));
    }

    private DriveLifecycle() {
    }

    /** Whether {@code from → to} is a legal drive transition. */
    public static boolean canTransition(DriveStatus from, DriveStatus to) {
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }

    /** Enforces a legal transition, else 409 {@code ILLEGAL_STATE_TRANSITION} naming the from/to states. */
    public static void requireTransition(DriveStatus from, DriveStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot move a drive from " + from + " to " + to + ".");
        }
    }
}
