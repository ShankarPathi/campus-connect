package com.campusconnect.common.domain;

/**
 * The lifecycle states of a {@link PlacementRecord} (architecture §5/§8, Epic 7). A record is created in
 * {@code PENDING_CONFIRMATION} when a student accepts an offer (Story 7.3); the College Admin's two-step
 * confirmation moves it to {@code OFFICIALLY_PLACED} (Story 7.4) — and <b>only</b> {@code OFFICIALLY_PLACED}
 * records count in placement reports (Epic 8). The {@code PlacementLifecycle} is introduced at that first
 * transition (7.4), mirroring how {@code OfferLifecycle} arrived at 7.2. Stored as the enum name.
 */
public enum PlacementStatus {
    PENDING_CONFIRMATION,
    OFFICIALLY_PLACED
}
