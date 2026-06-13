package com.campusconnect.recruiter.drives;

import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.repository.RecruiterProfileRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Author and edit a recruiter's own placement drive drafts (Story 4.1, FR-10).
 *
 * <p>Ownership is two-axis: the tenant-aware {@link DriveRepository} scopes every read/write to the
 * recruiter's tenant, and {@code createdBy = }{@link TenantContext#getUserId()} scopes them to the
 * recruiter — so a drive belonging to another recruiter (same tenant) or another tenant resolves to a
 * 404. A drive is editable only while {@code DRAFT}; the submit transition is Story 4.2.
 */
@Service
public class DriveService {

    private final DriveRepository driveRepository;
    private final TenantRepository tenantRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;

    public DriveService(DriveRepository driveRepository, TenantRepository tenantRepository,
                        RecruiterProfileRepository recruiterProfileRepository) {
        this.driveRepository = driveRepository;
        this.tenantRepository = tenantRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
    }

    /** Create a DRAFT drive owned by the calling recruiter, snapshotting their company name. */
    public DriveResponse create(DriveRequest request) {
        String recruiterId = currentRecruiterId();
        Drive drive = new Drive();
        drive.setCreatedBy(recruiterId);
        drive.setCompanyName(companyNameOf(recruiterId));
        drive.setStatus(DriveStatus.DRAFT);
        apply(request, drive);
        validateAgainstTenant(drive);
        return DriveResponse.of(driveRepository.save(drive));
    }

    /** Update one of the caller's own drives — only while DRAFT (full replace of editable fields). */
    public DriveResponse update(String id, DriveRequest request) {
        Drive drive = loadMyDrive(id);
        if (drive.getStatus() != DriveStatus.DRAFT) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a draft drive can be edited.");
        }
        apply(request, drive);
        validateAgainstTenant(drive);
        return DriveResponse.of(driveRepository.save(drive));
    }

    /**
     * Submit one of the caller's own DRAFT drives for College-Admin approval (Story 4.2): {@code DRAFT →
     * PENDING_APPROVAL}, gated by completeness. State guard first (must be DRAFT), then the completeness
     * gate ({@link #isSubmittable}). After this the drive is frozen from recruiter edits (the DRAFT-only
     * {@link #update} guard) until the admin acts (Story 4.3).
     */
    public DriveResponse submit(String id) {
        Drive drive = loadMyDrive(id);
        if (drive.getStatus() != DriveStatus.DRAFT) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a draft drive can be submitted.");
        }
        if (!isSubmittable(drive)) {
            throw new BusinessException(ErrorCode.DRIVE_INCOMPLETE,
                    "Complete all required fields and set a future deadline before submitting.");
        }
        // Re-assert the create/update invariant at the promotion gate: the college may have dropped a
        // branch/batch since the draft was last saved — a stale drive must not reach the approval queue.
        validateAgainstTenant(drive);
        drive.setStatus(DriveStatus.PENDING_APPROVAL);
        return DriveResponse.of(driveRepository.save(drive));
    }

    /** One of the caller's own drives (404 for another recruiter's / another tenant's / missing). */
    public DriveResponse getMyDrive(String id) {
        return DriveResponse.of(loadMyDrive(id));
    }

    /** The caller's own drives. */
    public List<DriveResponse> listMyDrives() {
        return driveRepository.findByCreatedBy(currentRecruiterId()).stream()
                .map(DriveResponse::of)
                .toList();
    }

    // ── internals ──

    private String currentRecruiterId() {
        return TenantContext.getUserId();
    }

    private Drive loadMyDrive(String id) {
        return driveRepository.findByIdAndCreatedBy(id, currentRecruiterId())
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found"));
    }

    private String companyNameOf(String recruiterId) {
        // A registered recruiter always has a profile (Story 2.2) — its absence is a server-side data
        // inconsistency, not bad client input, so signal 500 rather than a misleading 400.
        return recruiterProfileRepository
                .findByUserIdAndTenantId(recruiterId, TenantContext.requireTenantId())
                .map(RecruiterProfile::getCompanyName)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Recruiter profile not found for the current user."));
    }

    /** Full replace of the editable fields from the request (null eligibility clears to empty). */
    private void apply(DriveRequest request, Drive drive) {
        drive.setRole(request.role());
        drive.setPackageLpa(request.packageLpa());
        drive.setLocation(request.location());
        drive.setOpenings(request.openings());
        drive.setApplyDeadline(request.applyDeadline());

        EligibilityCriteria criteria = new EligibilityCriteria();
        EligibilityCriteriaRequest e = request.eligibility();
        if (e != null) {
            criteria.setBranches(cleanBranches(e.branches()));
            criteria.setMinCgpa(e.minCgpa());
            criteria.setBacklogPolicy(e.backlogPolicy());
            criteria.setBatch(e.batch());
        }
        drive.setEligibility(criteria);
    }

    /** Drop null/blank branch entries so junk is never stored (the review caught {@code ["", "CSE"]}). */
    private static List<String> cleanBranches(List<String> branches) {
        List<String> cleaned = new ArrayList<>();
        if (branches != null) {
            for (String b : branches) {
                if (b != null && !b.isBlank()) {
                    cleaned.add(b);
                }
            }
        }
        return cleaned;
    }

    /** Branches must be the tenant's offered branches; batch one of its batches (when provided). */
    private void validateAgainstTenant(Drive drive) {
        Tenant tenant = tenantRepository.findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found."));

        EligibilityCriteria e = drive.getEligibility();
        for (String branch : e.getBranches()) {
            if (branch != null && !branch.isBlank() && !tenant.getBranches().contains(branch)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Branch is not offered by your college.",
                        Map.of("eligibility.branches", "unknown branch: " + branch));
            }
        }
        String batch = e.getBatch();
        if (batch != null && !batch.isBlank() && !tenant.getBatches().contains(batch)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Batch is not offered by your college.", Map.of("eligibility.batch", "unknown batch: " + batch));
        }
    }

    /**
     * Whether a drive has everything required to be submitted for approval (Story 4.2 consumes this;
     * not called in 4.1). All core fields set, at least one branch, all criteria present, and a future
     * deadline.
     */
    static boolean isSubmittable(Drive d) {
        EligibilityCriteria e = d.getEligibility();
        return d.getRole() != null && !d.getRole().isBlank()
                && d.getPackageLpa() != null
                && d.getLocation() != null && !d.getLocation().isBlank()
                && d.getOpenings() != null && d.getOpenings() >= 1
                && d.getApplyDeadline() != null && d.getApplyDeadline().isAfter(Instant.now())
                && e.getBranches() != null && !e.getBranches().isEmpty()
                && e.getMinCgpa() != null && e.getBacklogPolicy() != null
                && e.getBatch() != null && !e.getBatch().isBlank();
    }
}
