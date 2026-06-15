package com.campusconnect.recruiter.applications;

import com.campusconnect.common.domain.AcademicDetails;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.PersonalDetails;
import com.campusconnect.common.domain.PlacementDetails;
import com.campusconnect.common.domain.StudentProfile;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * One applicant row in a recruiter's drive applicant list (Story 6.1, FR-18 / NFR-3).
 *
 * <p><b>Data minimization is by construction:</b> {@link #of} reads <i>only</i> hiring-relevant fields
 * ({@code phone} is kept — recruiters contact candidates — per Decision C). The restricted PII —
 * {@code address}, {@code dateOfBirth}, {@code gender} (and the internal {@code resumeSnapshotKey}) — is
 * <b>never</b> copied here, so it cannot leak to a recruiter even if {@link PersonalDetails} grows new
 * fields. The résumé is fetched separately via a short-lived pre-signed URL
 * ({@code …/{applicationId}/resume}); no key or URL is carried in the list.
 *
 * <p>{@code @JsonInclude(NON_NULL)} keeps the wire shape lean — a row whose profile has vanished still
 * renders with {@code applicationId}/{@code status}/{@code appliedAt}, the profile-derived fields simply absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicantSummaryResponse(
        String applicationId,
        String status,
        Instant appliedAt,
        String fullName,
        String phone,
        String rollNumber,
        String batch,
        String branch,
        Double cgpa,
        Integer activeBacklogs,
        List<String> skills,
        String expectedRole,
        String about,
        Boolean isPlaced) {

    public static ApplicantSummaryResponse of(Application app, StudentProfile profile) {
        String fullName = null;
        String phone = null;
        String rollNumber = null;
        String batch = null;
        String branch = null;
        Double cgpa = null;
        Integer activeBacklogs = null;
        List<String> skills = null;
        String expectedRole = null;
        String about = null;
        Boolean isPlaced = null;

        if (profile != null) {
            rollNumber = profile.getRollNumber();
            batch = profile.getBatch();
            isPlaced = profile.isPlaced();

            PersonalDetails personal = profile.getPersonal();
            if (personal != null) {
                fullName = personal.getFullName();
                phone = personal.getPhone(); // kept (Decision C); address / dateOfBirth / gender deliberately omitted
            }
            AcademicDetails academic = profile.getAcademic();
            if (academic != null) {
                branch = academic.getBranch();
                cgpa = academic.getCgpa();
                activeBacklogs = academic.getActiveBacklogs();
            }
            PlacementDetails placement = profile.getPlacement();
            if (placement != null) {
                skills = placement.getSkills();
                expectedRole = placement.getExpectedRole();
                about = placement.getAbout();
            }
        }

        return new ApplicantSummaryResponse(
                app.getId(),
                app.getStatus() != null ? app.getStatus().name() : null,
                app.getAppliedAt(),
                fullName, phone, rollNumber, batch,
                branch, cgpa, activeBacklogs,
                skills, expectedRole, about, isPlaced);
    }
}
