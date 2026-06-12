package com.campusconnect.common.profile;

import com.campusconnect.common.domain.StudentProfile;

/**
 * Pure completion calculator for a {@link StudentProfile} (Story 3.1, FR-7). Completion is the percent
 * of the <b>required</b> field set that is filled — optional fields never lower it. The required set is
 * the eligibility-critical data plus basic identity: full name, phone, roll number, batch, branch,
 * CGPA, active backlogs, and at least one skill (8 fields). A profile is submittable only at 100.
 *
 * <p>Boxed {@code cgpa}/{@code activeBacklogs} let a real {@code 0} count as filled while {@code null}
 * (never entered) does not.
 */
public final class ProfileCompletion {

    private static final int REQUIRED_FIELDS = 8;

    private ProfileCompletion() {
    }

    /** Percent (0–100) of the required field set that is filled. */
    public static int percentOf(StudentProfile p) {
        return filledCount(p) * 100 / REQUIRED_FIELDS;
    }

    /**
     * Whether every required field is present — the submit gate. Counts fields directly (not
     * {@code percentOf == 100}) so the gate stays correct even if {@code REQUIRED_FIELDS} ever stops
     * dividing 100 evenly.
     */
    public static boolean isComplete(StudentProfile p) {
        return filledCount(p) == REQUIRED_FIELDS;
    }

    /** Number of the required fields that are filled (0..{@value #REQUIRED_FIELDS}). */
    private static int filledCount(StudentProfile p) {
        int filled = 0;
        if (hasText(p.getPersonal().getFullName())) filled++;
        if (hasText(p.getPersonal().getPhone())) filled++;
        if (hasText(p.getRollNumber())) filled++;
        if (hasText(p.getBatch())) filled++;
        if (hasText(p.getAcademic().getBranch())) filled++;
        if (p.getAcademic().getCgpa() != null) filled++;
        if (p.getAcademic().getActiveBacklogs() != null) filled++;
        if (p.getPlacement().getSkills() != null && !p.getPlacement().getSkills().isEmpty()) filled++;
        return filled;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
