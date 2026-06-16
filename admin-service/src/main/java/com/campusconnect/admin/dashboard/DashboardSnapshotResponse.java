package com.campusconnect.admin.dashboard;

/**
 * The College-Admin's one-screen season snapshot (Story 8.4, FR-27) — 3 pending-approval action counts +
 * 4 season counts, every figure scoped to the admin's tenant. {@code placedStudents} counts only
 * {@code OFFICIALLY_PLACED} placement records (per {@code PlacementStatus} — the report-worthy figure).
 */
public record DashboardSnapshotResponse(long pendingProfileApprovals,
                                        long pendingRecruiterApprovals,
                                        long pendingDriveApprovals,
                                        long totalStudents,
                                        long totalDrives,
                                        long totalApplications,
                                        long placedStudents) {
}
