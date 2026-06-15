package com.campusconnect.common.domain;

/**
 * The per-student result of one interview round (architecture §8; Story 6.3 writes only {@code PENDING},
 * Story 6.4 records the outcome). Only {@code PASS} provisions the next round's assignment (FR-21). Stored
 * as the enum name on an {@link ApplicationRound}.
 */
public enum RoundResult {
    PENDING,
    PASS,
    FAIL,
    ABSENT
}
