package com.campusconnect.student.applications;

import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationLifecycle;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Withdraw a student's own application (Story 5.5, FR-16) — the {@link Application} state machine's first
 * transition. Owner-scoped (the student can only withdraw their own application); the withdraw is allowed
 * only pre-shortlist, enforced by the canonical {@link ApplicationLifecycle}. The {@code @Version} on
 * {@link Application} (Story 5.4) makes the {@code WITHDRAWN} save optimistic-lock-safe against a
 * concurrent recruiter/admin transition.
 */
@Service
public class WithdrawService {

    private final ApplicationRepository applicationRepository;
    private final DriveRepository driveRepository;

    public WithdrawService(ApplicationRepository applicationRepository, DriveRepository driveRepository) {
        this.applicationRepository = applicationRepository;
        this.driveRepository = driveRepository;
    }

    public ApplicationResponse withdraw(String applicationId) {
        String studentId = TenantContext.getUserId();

        // Owner-and-tenant scoped: another student's / another tenant's / a missing application is 404.
        Application application = applicationRepository.findByIdAndStudentId(applicationId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Application not found."));

        // Withdraw is legal only pre-shortlist; otherwise 409 WITHDRAW_NOT_ALLOWED, status unchanged.
        ApplicationLifecycle.requireWithdrawable(application.getStatus());

        application.setStatus(ApplicationStatus.WITHDRAWN);
        Application saved = applicationRepository.save(application);

        Drive drive = driveRepository.findById(saved.getDriveId()).orElse(null);
        return ApplicationResponse.of(saved, drive);
    }
}
