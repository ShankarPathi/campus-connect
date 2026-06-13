package com.campusconnect.student.profile;

import com.campusconnect.common.domain.AcademicDetails;
import com.campusconnect.common.domain.PersonalDetails;
import com.campusconnect.common.domain.PlacementDetails;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.profile.ProfileCompletion;
import com.campusconnect.common.repository.StudentProfileRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Build/submit the authenticated student's own placement profile (Story 3.1, FR-7).
 *
 * <p>Ownership is structural: {@code studentId} is ALWAYS {@link TenantContext#getUserId()} — never a
 * body value — and there is no profile-id in the API, so a student can only ever touch their own
 * profile. Tenant isolation is automatic via the tenant-aware {@link StudentProfileRepository}.
 */
@Service
public class StudentProfileService {

    private final StudentProfileRepository profileRepository;
    private final TenantRepository tenantRepository;

    public StudentProfileService(StudentProfileRepository profileRepository, TenantRepository tenantRepository) {
        this.profileRepository = profileRepository;
        this.tenantRepository = tenantRepository;
    }

    /** The caller's profile, or a fresh empty DRAFT view (not persisted) if they have none yet. */
    public StudentProfileResponse getMyProfile() {
        StudentProfile profile = profileRepository.findByStudentId(currentStudentId())
                .orElseGet(this::initialDraft);
        return StudentProfileResponse.from(profile);
    }

    /** Save the caller's profile as a draft (full replace of editable fields). Recomputes completion. */
    public StudentProfileResponse saveMyProfile(StudentProfileRequest request) {
        String studentId = currentStudentId();
        StudentProfile profile = profileRepository.findByStudentId(studentId).orElseGet(this::initialDraft);

        // Edit guard (decision A): a submitted profile is not silently mutated — only DRAFT/REJECTED edit.
        if (profile.getId() != null && !isEditable(profile.getProfileApprovalStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A submitted profile cannot be edited until it has been reviewed.");
        }

        validateAgainstTenant(request);
        apply(request, profile);
        profile.setStudentId(studentId);
        profile.setCompletionPercent(ProfileCompletion.percentOf(profile));
        return StudentProfileResponse.from(profileRepository.save(profile));
    }

    /** Submit the caller's profile for approval: DRAFT → PENDING_APPROVAL, only when complete. */
    public StudentProfileResponse submitMyProfile() {
        StudentProfile profile = profileRepository.findByStudentId(currentStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_INCOMPLETE,
                        "Complete your profile before submitting."));

        // Submittable from DRAFT or REJECTED (re-submit after a College-Admin rejection, Story 3.3) —
        // the same editable states, so reuse isEditable.
        if (!isEditable(profile.getProfileApprovalStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a draft or rejected profile can be submitted.");
        }

        profile.setCompletionPercent(ProfileCompletion.percentOf(profile));
        if (!ProfileCompletion.isComplete(profile)) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE,
                    "Fill all required fields before submitting.");
        }

        profile.setProfileApprovalStatus(ProfileApprovalStatus.PENDING_APPROVAL);
        profile.setRejectionReason(null); // a fresh submission clears any prior rejection reason
        return StudentProfileResponse.from(profileRepository.save(profile));
    }

    // ── internals ──

    private String currentStudentId() {
        return TenantContext.getUserId();
    }

    /** A profile is editable — and re-submittable — only in DRAFT or REJECTED. */
    private static boolean isEditable(ProfileApprovalStatus status) {
        return status == ProfileApprovalStatus.DRAFT || status == ProfileApprovalStatus.REJECTED;
    }

    private StudentProfile initialDraft() {
        StudentProfile p = new StudentProfile();
        p.setStudentId(currentStudentId());
        return p; // status DRAFT, completion 0 by construction
    }

    /** Branch must be one of the tenant's offered branches; batch one of its batches (when provided). */
    private void validateAgainstTenant(StudentProfileRequest request) {
        Tenant tenant = tenantRepository.findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found."));

        String branch = request.academic() != null ? request.academic().branch() : null;
        if (branch != null && !branch.isBlank() && !tenant.getBranches().contains(branch)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Branch is not offered by your college.", Map.of("academic.branch", "unknown branch: " + branch));
        }
        String batch = request.batch();
        if (batch != null && !batch.isBlank() && !tenant.getBatches().contains(batch)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Batch is not offered by your college.", Map.of("batch", "unknown batch: " + batch));
        }
    }

    /** Full replace of the editable fields from the request (null sections clear to empty). */
    private void apply(StudentProfileRequest request, StudentProfile profile) {
        profile.setRollNumber(request.rollNumber());
        profile.setBatch(request.batch());

        PersonalDetails personal = new PersonalDetails();
        if (request.personal() != null) {
            personal.setFullName(request.personal().fullName());
            personal.setPhone(request.personal().phone());
            personal.setGender(request.personal().gender());
            personal.setDateOfBirth(request.personal().dateOfBirth());
            personal.setAddress(request.personal().address());
        }
        profile.setPersonal(personal);

        AcademicDetails academic = new AcademicDetails();
        if (request.academic() != null) {
            academic.setBranch(request.academic().branch());
            academic.setCgpa(request.academic().cgpa());
            academic.setActiveBacklogs(request.academic().activeBacklogs());
        }
        profile.setAcademic(academic);

        PlacementDetails placement = new PlacementDetails();
        if (request.placement() != null) {
            List<String> skills = request.placement().skills() != null
                    ? new ArrayList<>(request.placement().skills()) : new ArrayList<>();
            placement.setSkills(skills);
            placement.setExpectedRole(request.placement().expectedRole());
            placement.setAbout(request.placement().about());
        }
        profile.setPlacement(placement);
    }
}
