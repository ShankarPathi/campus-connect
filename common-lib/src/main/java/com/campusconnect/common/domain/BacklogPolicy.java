package com.campusconnect.common.domain;

/**
 * A drive's stance on a student's active backlogs, part of {@link EligibilityCriteria} (Story 4.1).
 * The Epic-5 eligibility engine reads this for rule 9 ("backlog policy satisfied"):
 * {@code NO_BACKLOG} → require {@code activeBacklogs == 0}; {@code ALLOW_BACKLOG} → any.
 */
public enum BacklogPolicy {
    NO_BACKLOG,
    ALLOW_BACKLOG
}
