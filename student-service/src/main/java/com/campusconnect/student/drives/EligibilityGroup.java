package com.campusconnect.student.drives;

/**
 * The bucket a drive falls into on the student's pre-apply drive list (Story 5.3, FR-13).
 * Precedence when classifying: {@code APPLIED} → {@code CLOSED} → engine verdict
 * ({@code ELIGIBLE} / {@code NOT_ELIGIBLE}).
 */
public enum EligibilityGroup {
    ELIGIBLE,
    APPLIED,
    NOT_ELIGIBLE,
    CLOSED
}
