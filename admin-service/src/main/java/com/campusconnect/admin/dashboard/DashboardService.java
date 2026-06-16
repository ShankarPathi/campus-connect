package com.campusconnect.admin.dashboard;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.PlacementStatus;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.repository.PlacementRecordRepository;
import com.campusconnect.common.repository.StudentProfileRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.tenancy.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Assembles the College-Admin season snapshot (Story 8.4, FR-27) from simple per-collection counts — no
 * aggregation pipelines (those are Story 8.5's reports). Every figure is tenant-scoped: the tenant-aware
 * repositories auto-append the tenant criterion; the not-tenant-aware {@link UserRepository} is passed
 * {@link TenantContext#requireTenantId()} explicitly for its two role counts. Read-only; computed fresh per
 * request (the counts are cheap and tenant-bounded).
 */
@Service
public class DashboardService {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final PlacementRecordRepository placementRecordRepository;

    public DashboardService(StudentProfileRepository studentProfileRepository,
                            UserRepository userRepository,
                            DriveRepository driveRepository,
                            ApplicationRepository applicationRepository,
                            PlacementRecordRepository placementRecordRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.placementRecordRepository = placementRecordRepository;
    }

    /** The current admin's tenant snapshot — 3 pending-approval counts + 4 season counts. */
    public DashboardSnapshotResponse snapshot() {
        String tenantId = TenantContext.requireTenantId();
        return new DashboardSnapshotResponse(
                studentProfileRepository.countByApprovalStatus(ProfileApprovalStatus.PENDING_APPROVAL),
                userRepository.countByTenantIdAndRoleAndAccountStatus(tenantId, Role.RECRUITER, AccountStatus.PENDING_APPROVAL),
                driveRepository.countByStatus(DriveStatus.PENDING_APPROVAL),
                userRepository.countByTenantIdAndRole(tenantId, Role.STUDENT),
                driveRepository.count(),
                applicationRepository.count(),
                placementRecordRepository.countDistinctStudentsByStatus(PlacementStatus.OFFICIALLY_PLACED));
    }
}
