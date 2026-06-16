package com.campusconnect.admin.reports;

import java.util.List;

/**
 * The College-Admin placement report (Story 8.5, FR-26) — overall %, branch-wise, and company-wise
 * breakdowns, tenant-scoped, counting only OFFICIALLY_PLACED placements.
 */
public record PlacementReportResponse(OverallStats overall, List<BranchStat> branchwise, List<CompanyStat> companywise) {
}
