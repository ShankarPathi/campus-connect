package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A student's placement-eligibility profile (Story 3.1, FR-7). Tenant-scoped and owned by exactly one
 * student: {@code studentId} is the authenticated principal (the {@link User} id), and there is one
 * profile per student per tenant (unique {@code {tenantId, studentId}}). The embedded sub-documents
 * carry the fields the Epic-5 eligibility engine reads ({@code academic.branch/cgpa/activeBacklogs},
 * {@code batch}, {@code isPlaced}).
 *
 * <p>{@code profileApprovalStatus} moves {@code DRAFT → PENDING_APPROVAL} on submit; approve/reject is
 * Story 3.3 and the season edit-lock ({@code isLocked}) is Story 3.4 — neither field/behaviour is built here.
 */
@Document("studentProfiles")
@CompoundIndex(name = "uniq_tenant_student", def = "{'tenantId': 1, 'studentId': 1}", unique = true)
public class StudentProfile extends TenantAwareDocument {

    private String studentId;
    private String rollNumber;
    private String batch;
    private PersonalDetails personal = new PersonalDetails();
    private AcademicDetails academic = new AcademicDetails();
    private PlacementDetails placement = new PlacementDetails();
    private ProfileApprovalStatus profileApprovalStatus = ProfileApprovalStatus.DRAFT;
    /** Set by a College-Admin rejection (Story 3.3); cleared on the student's re-submit. Null otherwise. */
    private String rejectionReason;
    private boolean isPlaced = false;
    private int completionPercent = 0;

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public PersonalDetails getPersonal() {
        return personal;
    }

    public void setPersonal(PersonalDetails personal) {
        this.personal = personal;
    }

    public AcademicDetails getAcademic() {
        return academic;
    }

    public void setAcademic(AcademicDetails academic) {
        this.academic = academic;
    }

    public PlacementDetails getPlacement() {
        return placement;
    }

    public void setPlacement(PlacementDetails placement) {
        this.placement = placement;
    }

    public ProfileApprovalStatus getProfileApprovalStatus() {
        return profileApprovalStatus;
    }

    public void setProfileApprovalStatus(ProfileApprovalStatus profileApprovalStatus) {
        this.profileApprovalStatus = profileApprovalStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public boolean isPlaced() {
        return isPlaced;
    }

    public void setPlaced(boolean placed) {
        isPlaced = placed;
    }

    public int getCompletionPercent() {
        return completionPercent;
    }

    public void setCompletionPercent(int completionPercent) {
        this.completionPercent = completionPercent;
    }
}
