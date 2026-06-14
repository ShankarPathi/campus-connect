package com.campusconnect.admin.drives;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * College-Admin approval/rejection/correction of recruiter drives (Story 4.3, FR-11). Every operation is
 * scoped to the calling admin's tenant via the tenant-aware {@link DriveRepository#findById}: a drive in
 * another tenant is simply not found (404) — the cross-tenant isolation guard, no owner branch (unlike the
 * recruiter side, the admin acts on any drive in their tenant). Decisions/edits are written to the
 * append-only audit trail; the recruiter is notified best-effort (the 2.2/3.3 pattern).
 *
 * <p>The eligible-student notification on publish is deliberately deferred to Epic 8 (it needs the Epic-5
 * eligibility engine + the §9 EventPublisher/notifications/outbox); only the recruiter is notified here.
 */
@Service
public class DriveApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DriveApprovalService.class);
    private static final String ENTITY_TYPE = "Drive";

    private final DriveRepository driveRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    public DriveApprovalService(DriveRepository driveRepository, TenantRepository tenantRepository,
                                UserRepository userRepository, EmailService emailService, AuditService auditService) {
        this.driveRepository = driveRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    /** Drives of the admin's tenant in the given status — the review queue. DRAFT is a recruiter's private WIP. */
    public List<PendingDriveResponse> listByStatus(DriveStatus status) {
        if (status == DriveStatus.DRAFT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Draft drives are private to the recruiter and not visible to admins.",
                    Map.of("status", "DRAFT is not listable"));
        }
        return driveRepository.findByStatus(status).stream()
                .map(PendingDriveResponse::of)
                .toList();
    }

    /** Approve a PENDING_APPROVAL drive in the admin's tenant → PUBLISHED; audit + notify the recruiter. */
    public void approve(String driveId) {
        Drive drive = loadPendingDrive(driveId);
        drive.setStatus(DriveStatus.PUBLISHED);
        driveRepository.save(drive);
        auditService.record(AuditAction.DRIVE_APPROVED, ENTITY_TYPE, drive.getId(),
                "status=PENDING_APPROVAL", "status=PUBLISHED");
        notifyRecruiter(drive,
                "Your Campus Connect drive is approved",
                "Your drive \"%s\" has been approved and is now published to eligible students."
                        .formatted(safeRole(drive)));
    }

    /** Reject a PENDING_APPROVAL drive in the admin's tenant → REJECTED_BY_ADMIN + reason; audit + notify. */
    public void reject(String driveId, String reason) {
        Drive drive = loadPendingDrive(driveId);
        drive.setStatus(DriveStatus.REJECTED_BY_ADMIN);
        drive.setRejectionReason(reason);
        driveRepository.save(drive);
        auditService.record(AuditAction.DRIVE_REJECTED, ENTITY_TYPE, drive.getId(),
                "status=PENDING_APPROVAL", "status=REJECTED_BY_ADMIN; reason=" + reason);
        notifyRecruiter(drive,
                "Your Campus Connect drive was not approved",
                "Your drive \"%s\" was not approved.%n%nReason: %s%n%nYou can correct it and re-submit."
                        .formatted(safeRole(drive), reason));
    }

    /**
     * Admin correction of a PENDING_APPROVAL drive's eligibility criteria in the admin's tenant. Only the
     * provided fields are applied, validated against the tenant; the status is unchanged. The before/after
     * of every field that actually changed is written to the audit trail.
     */
    public void editCriteria(String driveId, AdminEditDriveCriteriaRequest request) {
        Drive drive = loadPendingDrive(driveId);
        Tenant tenant = tenantRepository.findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found."));

        EligibilityCriteria criteria = drive.getEligibility();
        // Clean the admin's branches like the recruiter side (strip null/blank, dedupe); a provided-but-empty
        // set is rejected — the admin must not be able to make a submittable drive un-eligible (zero branches).
        List<String> newBranches = request.branches() == null ? null : cleanBranches(request.branches());
        if (newBranches != null && newBranches.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "A drive must target at least one branch.", Map.of("eligibility.branches", "must not be empty"));
        }
        // Set-compare so a mere reorder of the same branches is not a spurious "change" (no audit/no write).
        boolean branchesChange = newBranches != null
                && !new HashSet<>(newBranches).equals(new HashSet<>(criteria.getBranches()));
        boolean batchChange = request.batch() != null && !request.batch().equals(criteria.getBatch());

        // Validate every changing field BEFORE mutating anything (validation gates mutation — the 3.3 lesson).
        if (branchesChange) {
            for (String branch : newBranches) {
                requireMember(tenant.getBranches(), branch, "eligibility.branches", "branch");
            }
        }
        if (batchChange) {
            requireMember(tenant.getBatches(), request.batch(), "eligibility.batch", "batch");
        }

        List<String> oldParts = new ArrayList<>();
        List<String> newParts = new ArrayList<>();
        if (branchesChange) {
            oldParts.add("branches=" + criteria.getBranches());
            newParts.add("branches=" + newBranches);
            criteria.setBranches(new ArrayList<>(newBranches));
        }
        if (request.minCgpa() != null && !request.minCgpa().equals(criteria.getMinCgpa())) {
            oldParts.add("minCgpa=" + criteria.getMinCgpa());
            newParts.add("minCgpa=" + request.minCgpa());
            criteria.setMinCgpa(request.minCgpa());
        }
        if (request.backlogPolicy() != null && !request.backlogPolicy().equals(criteria.getBacklogPolicy())) {
            oldParts.add("backlogPolicy=" + criteria.getBacklogPolicy());
            newParts.add("backlogPolicy=" + request.backlogPolicy());
            criteria.setBacklogPolicy(request.backlogPolicy());
        }
        if (batchChange) {
            oldParts.add("batch=" + criteria.getBatch());
            newParts.add("batch=" + request.batch());
            criteria.setBatch(request.batch());
        }

        if (oldParts.isEmpty()) {
            return; // nothing changed — no write, no audit row
        }

        driveRepository.save(drive);
        auditService.record(AuditAction.DRIVE_EDITED, ENTITY_TYPE, drive.getId(),
                String.join(", ", oldParts), String.join(", ", newParts));
    }

    // ── internals ──

    /** Strip null/blank entries and dedupe (order-preserving) — mirrors the recruiter-side branch cleaning. */
    private static List<String> cleanBranches(List<String> branches) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String b : branches) {
            if (b != null && !b.isBlank()) {
                cleaned.add(b);
            }
        }
        return new ArrayList<>(cleaned);
    }

    private void requireMember(List<String> allowed, String value, String field, String label) {
        if (!allowed.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Unknown " + label + " for this college.", Map.of(field, "unknown " + label + ": " + value));
        }
    }

    /** Tenant-scoped drive (404 for another tenant / missing) + the PENDING_APPROVAL state guard. */
    private Drive loadPendingDrive(String driveId) {
        Drive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found"));
        if (drive.getStatus() != DriveStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Drive is not awaiting approval (status: " + drive.getStatus() + ")");
        }
        return drive;
    }

    private static String safeRole(Drive drive) {
        return drive.getRole() != null ? drive.getRole() : "(untitled)";
    }

    /**
     * Best-effort notification of the recruiter who created the drive (the 2.2/3.3 pattern): the decision
     * is already committed and authoritative, so a transient SMTP failure must not fail the request (the
     * status is no longer PENDING_APPROVAL → a retry would 409). Epic 8 replaces this with the durable
     * outbox and adds the eligible-student fan-out.
     */
    private void notifyRecruiter(Drive drive, String subject, String body) {
        try {
            userRepository.findById(drive.getCreatedBy())
                    .ifPresent(u -> emailService.sendEmail(u.getEmail(), subject, body));
        } catch (RuntimeException ex) {
            log.warn("Failed to send drive decision notification for drive {} (decision stands)", drive.getId(), ex);
        }
    }
}
