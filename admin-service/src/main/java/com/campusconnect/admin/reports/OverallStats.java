package com.campusconnect.admin.reports;

/** The season's overall placement figures (Story 8.5) — placed = distinct OFFICIALLY_PLACED students. */
public record OverallStats(long totalStudents, long placedStudents, double placementPercent) {
}
