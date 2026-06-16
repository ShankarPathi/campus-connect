package com.campusconnect.common.domain;

import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.web.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single canonical definition of the {@link PlacementRecord} state machine (architecture §8, FR-25) —
 * mirroring {@link OfferLifecycle}. Every placement status change must be a legal edge here.
 *
 * <pre>
 *   PENDING_CONFIRMATION → OFFICIALLY_PLACED
 *   OFFICIALLY_PLACED → (terminal)
 * </pre>
 *
 * <p>Introduced at the placement's <b>first (and only) transition</b> — the Story 7.4 admin confirmation
 * ({@code PENDING_CONFIRMATION → OFFICIALLY_PLACED}), exactly as {@link OfferLifecycle} arrived at Story 7.2.
 * Confirm-only by design (MVP): there is no un-confirm/reject edge — a placement that should not be confirmed
 * is left {@code PENDING_CONFIRMATION} (un-counted in reports). Pure (no Spring).
 */
public final class PlacementLifecycle {

    private static final Map<PlacementStatus, Set<PlacementStatus>> LEGAL = new EnumMap<>(PlacementStatus.class);

    static {
        LEGAL.put(PlacementStatus.PENDING_CONFIRMATION, EnumSet.of(PlacementStatus.OFFICIALLY_PLACED));
        LEGAL.put(PlacementStatus.OFFICIALLY_PLACED, EnumSet.noneOf(PlacementStatus.class));
    }

    private PlacementLifecycle() {
    }

    /** Whether {@code from → to} is a legal placement transition. */
    public static boolean canTransition(PlacementStatus from, PlacementStatus to) {
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }

    /** Enforces a legal transition, else 409 {@code ILLEGAL_STATE_TRANSITION} (the Story 7.4 confirm gate). */
    public static void requireTransition(PlacementStatus from, PlacementStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot move a placement from " + from + " to " + to + ".");
        }
    }
}
