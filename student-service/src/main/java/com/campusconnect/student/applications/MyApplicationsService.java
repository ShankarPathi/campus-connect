package com.campusconnect.student.applications;

import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The student's "My Applications" read view (Story 5.6, FR-17) — all of the authenticated student's own
 * applications with their current status and drive context, derived live from {@code applications}
 * (+ {@code drives}). No duplicate/materialized store: the list is computed on read, ordered most-recent
 * first.
 */
@Service
public class MyApplicationsService {

    private final ApplicationRepository applicationRepository;
    private final DriveRepository driveRepository;

    public MyApplicationsService(ApplicationRepository applicationRepository, DriveRepository driveRepository) {
        this.applicationRepository = applicationRepository;
        this.driveRepository = driveRepository;
    }

    public List<ApplicationResponse> listMyApplications() {
        List<Application> applications = applicationRepository.findByStudentId(TenantContext.getUserId());

        // Batch-load every application's drive in one tenant-scoped query (no N+1).
        List<String> driveIds = applications.stream().map(Application::getDriveId).distinct().toList();
        Map<String, Drive> drivesById = driveRepository.findByIdIn(driveIds).stream()
                .collect(Collectors.toMap(Drive::getId, Function.identity()));

        return applications.stream()
                .sorted(Comparator.comparing(Application::getAppliedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))) // most-recent first, nulls last
                .map(a -> ApplicationResponse.of(a, drivesById.get(a.getDriveId())))
                .toList();
    }
}
