package com.campusconnect.common.domain;

import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.web.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single canonical definition of the {@link Offer} state machine (architecture §8, Epic 7) — mirroring
 * {@link ApplicationLifecycle}. Every offer status change must be a legal edge here, so an offer only ever
 * moves through valid states.
 *
 * <pre>
 *   PENDING  → ACCEPTED | DECLINED | EXPIRED
 *   ACCEPTED, DECLINED, EXPIRED → (terminal)
 * </pre>
 *
 * <p>Introduced at the offer's <b>first transition</b> — the Story 7.2 expiry job's {@code PENDING → EXPIRED}
 * (exactly as {@link ApplicationLifecycle} arrived at Story 5.5, not at the entity's creation in 7.1). Story
 * 7.3 reuses {@code PENDING → ACCEPTED | DECLINED} for the student's accept/decline. Pure (no Spring).
 */
public final class OfferLifecycle {

    private static final Map<OfferStatus, Set<OfferStatus>> LEGAL = new EnumMap<>(OfferStatus.class);

    static {
        LEGAL.put(OfferStatus.PENDING, EnumSet.of(
                OfferStatus.ACCEPTED, OfferStatus.DECLINED, OfferStatus.EXPIRED));
        LEGAL.put(OfferStatus.ACCEPTED, EnumSet.noneOf(OfferStatus.class));
        LEGAL.put(OfferStatus.DECLINED, EnumSet.noneOf(OfferStatus.class));
        LEGAL.put(OfferStatus.EXPIRED, EnumSet.noneOf(OfferStatus.class));
    }

    private OfferLifecycle() {
    }

    /** Whether {@code from → to} is a legal offer transition. */
    public static boolean canTransition(OfferStatus from, OfferStatus to) {
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }

    /** Enforces a legal transition, else 409 {@code ILLEGAL_STATE_TRANSITION} (the Epic 7 offer entry point). */
    public static void requireTransition(OfferStatus from, OfferStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot move an offer from " + from + " to " + to + ".");
        }
    }
}
