package com.campusconnect.student.profile;

import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;

import java.util.List;

/**
 * The student's view of their own profile (Story 3.1) — includes the derived {@code completionPercent}
 * and the {@code profileApprovalStatus}. Never exposes {@code tenantId}.
 */
public record StudentProfileResponse(
        String studentId,
        String rollNumber,
        String batch,
        Personal personal,
        Academic academic,
        Placement placement,
        ProfileApprovalStatus profileApprovalStatus,
        String rejectionReason,
        boolean isPlaced,
        int completionPercent) {

    public record Personal(String fullName, String phone, String gender, String dateOfBirth, String address) {
    }

    public record Academic(String branch, Double cgpa, Integer activeBacklogs) {
    }

    public record Placement(List<String> skills, String expectedRole, String about) {
    }

    public static StudentProfileResponse from(StudentProfile p) {
        return new StudentProfileResponse(
                p.getStudentId(),
                p.getRollNumber(),
                p.getBatch(),
                new Personal(p.getPersonal().getFullName(), p.getPersonal().getPhone(),
                        p.getPersonal().getGender(), p.getPersonal().getDateOfBirth(), p.getPersonal().getAddress()),
                new Academic(p.getAcademic().getBranch(), p.getAcademic().getCgpa(), p.getAcademic().getActiveBacklogs()),
                new Placement(p.getPlacement().getSkills(), p.getPlacement().getExpectedRole(), p.getPlacement().getAbout()),
                p.getProfileApprovalStatus(),
                p.getRejectionReason(),
                p.isPlaced(),
                p.getCompletionPercent());
    }
}
