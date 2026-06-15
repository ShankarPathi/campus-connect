package com.campusconnect.student.drives;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.PlacementPolicy;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.eligibility.EligibilityContext;
import com.campusconnect.common.eligibility.EligibilityEngine;
import com.campusconnect.common.eligibility.EligibilityReport;
import com.campusconnect.common.eligibility.PolicyResolver;
import com.campusconnect.common.eligibility.ResolvedPolicy;
import com.campusconnect.common.eligibility.RuleOutcome;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.repository.StudentProfileRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The student's pre-apply transparency list (Story 5.3, FR-13) — the first live consumer of the
 * eligibility engine (5.1) + policy resolver (5.2). For each discoverable drive in the student's tenant
 * it produces an {@link EligibilityGroup} and, for a not-eligible drive, the specific failed criteria.
 *
 * <p>This service is the <b>impure caller</b> that assembles the pure engine's inputs: it loads the
 * student's profile, applications, and tenant policy, reads the clock ({@code now}), and resolves the
 * per-drive policy — the engine itself stays pure.
 */
@Service
public class StudentDriveService {

    /** Statuses a student can see; DRAFT/PENDING_APPROVAL/REJECTED_BY_ADMIN/CANCELLED are invisible. */
    private static final Set<DriveStatus> DISCOVERABLE =
            Set.of(DriveStatus.PUBLISHED, DriveStatus.ONGOING, DriveStatus.CLOSED, DriveStatus.COMPLETED);
    /** Statuses an open (still-applyable) drive may be in. */
    private static final Set<DriveStatus> OPEN =
            Set.of(DriveStatus.PUBLISHED, DriveStatus.ONGOING);

    private final DriveRepository driveRepository;
    private final StudentProfileRepository profileRepository;
    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;

    public StudentDriveService(DriveRepository driveRepository, StudentProfileRepository profileRepository,
                               ApplicationRepository applicationRepository, TenantRepository tenantRepository) {
        this.driveRepository = driveRepository;
        this.profileRepository = profileRepository;
        this.applicationRepository = applicationRepository;
        this.tenantRepository = tenantRepository;
    }

    public List<StudentDriveResponse> listDrives() {
        String studentId = TenantContext.getUserId();
        StudentProfile profile = profileRepository.findByStudentId(studentId).orElse(null);
        Set<String> appliedDriveIds = applicationRepository.findByStudentId(studentId).stream()
                .map(Application::getDriveId)
                .collect(Collectors.toSet());
        PlacementPolicy tenantPolicy = tenantRepository.findById(TenantContext.requireTenantId())
                .map(Tenant::getPlacementPolicy)
                .orElse(null);
        Instant now = Instant.now();

        return driveRepository.findByStatusIn(DISCOVERABLE).stream()
                .map(drive -> classify(drive, profile, tenantPolicy, appliedDriveIds, now))
                .toList();
    }

    private StudentDriveResponse classify(Drive drive, StudentProfile profile, PlacementPolicy tenantPolicy,
                                          Set<String> appliedDriveIds, Instant now) {
        if (appliedDriveIds.contains(drive.getId())) {
            return StudentDriveResponse.of(drive, EligibilityGroup.APPLIED, List.of());
        }
        if (!OPEN.contains(drive.getStatus()) || isPastDeadline(drive, now)) {
            return StudentDriveResponse.of(drive, EligibilityGroup.CLOSED, List.of());
        }
        // Open + unapplied → the engine decides eligible vs not-eligible (with the resolved tenant policy).
        // alreadyApplied is passed false here intentionally: the APPLIED bucket above already short-circuits
        // every applied drive, so the engine's no-duplicate rule cannot (and must not) fire in this path.
        // A null profile is safe — every rule null-guards it, classifying a profileless student NOT_ELIGIBLE.
        ResolvedPolicy resolved = PolicyResolver.resolve(tenantPolicy, drive.getEligibility());
        EligibilityContext ctx = new EligibilityContext(
                AccountStatus.ACTIVE, profile, drive, resolved, false, now);
        EligibilityReport report = EligibilityEngine.checkAll(ctx);
        if (report.eligible()) {
            return StudentDriveResponse.of(drive, EligibilityGroup.ELIGIBLE, List.of());
        }
        List<String> failedCriteria = report.failures().stream().map(RuleOutcome::reason).toList();
        return StudentDriveResponse.of(drive, EligibilityGroup.NOT_ELIGIBLE, failedCriteria);
    }

    private static boolean isPastDeadline(Drive drive, Instant now) {
        return drive.getApplyDeadline() != null && now.isAfter(drive.getApplyDeadline());
    }
}
