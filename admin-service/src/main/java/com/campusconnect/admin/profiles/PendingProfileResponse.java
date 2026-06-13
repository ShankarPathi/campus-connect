package com.campusconnect.admin.profiles;

import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;

/**
 * A College Admin's review view of a student profile (Story 3.3) — the fields needed to decide approval
 * against college records. Exposes no internal ids beyond the studentId (the principal the admin acts on).
 */
public record PendingProfileResponse(
        String studentId,
        String rollNumber,
        String fullName,
        String branch,
        Double cgpa,
        Integer activeBacklogs,
        String batch,
        int completionPercent,
        ProfileApprovalStatus profileApprovalStatus,
        boolean isLocked) {

    public static PendingProfileResponse of(StudentProfile p) {
        return new PendingProfileResponse(
                p.getStudentId(),
                p.getRollNumber(),
                p.getPersonal().getFullName(),
                p.getAcademic().getBranch(),
                p.getAcademic().getCgpa(),
                p.getAcademic().getActiveBacklogs(),
                p.getBatch(),
                p.getCompletionPercent(),
                p.getProfileApprovalStatus(),
                p.isLocked());
    }
}
