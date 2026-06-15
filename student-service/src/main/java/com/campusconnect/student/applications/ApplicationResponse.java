package com.campusconnect.student.applications;

import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;

import java.time.Instant;

/**
 * The client-safe view of a student's {@link Application} (Story 5.4, FR-15). Company/role are read off
 * the applied-to {@link Drive}. The internal {@code resumeSnapshotKey} is deliberately <b>never</b>
 * exposed — like {@code Resume.s3Key}, it is reached only via short-lived pre-signed URLs in a later epic.
 */
public record ApplicationResponse(
        String id,
        String driveId,
        String companyName,
        String role,
        ApplicationStatus status,
        Instant appliedAt) {

    public static ApplicationResponse of(Application a, Drive drive) {
        return new ApplicationResponse(
                a.getId(), a.getDriveId(),
                drive != null ? drive.getCompanyName() : null,
                drive != null ? drive.getRole() : null,
                a.getStatus(), a.getAppliedAt());
    }
}
