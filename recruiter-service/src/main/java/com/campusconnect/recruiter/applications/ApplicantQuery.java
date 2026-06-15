package com.campusconnect.recruiter.applications;

import com.campusconnect.common.domain.ApplicationStatus;

import java.util.List;

/**
 * The bound query parameters for a recruiter applicant list (Story 6.1): an optional {@code status} filter,
 * a name/roll {@code search}, a {@code sortBy} field ({@code appliedAt} | {@code cgpa} | {@code name}) with
 * direction, and page coordinates. Any field may be null/blank — {@link ApplicantReviewService} applies the
 * defaults (no filter, sort {@code appliedAt} desc, page 0, size 20).
 */
public record ApplicantQuery(
        List<ApplicationStatus> status,
        String search,
        String sortBy,
        String sortDir,
        Integer page,
        Integer pageSize) {
}
