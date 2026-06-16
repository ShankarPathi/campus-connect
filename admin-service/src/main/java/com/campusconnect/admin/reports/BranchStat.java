package com.campusconnect.admin.reports;

/** One branch's placement breakdown (Story 8.5). */
public record BranchStat(String branch, long totalStudents, long placedStudents, double placementPercent) {
}
