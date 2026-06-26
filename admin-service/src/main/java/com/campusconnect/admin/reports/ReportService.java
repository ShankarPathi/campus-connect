package com.campusconnect.admin.reports;

import com.campusconnect.common.domain.PlacementRecord;
import com.campusconnect.common.domain.PlacementStatus;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.repository.BranchCount;
import com.campusconnect.common.repository.CompanyCount;
import com.campusconnect.common.repository.PlacementRecordRepository;
import com.campusconnect.common.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the College-Admin placement report + CSV export (Story 8.5, FR-26) — overall %, branch-wise, and
 * company-wise breakdowns, plus a detailed officially-placed CSV, tenant-scoped, counting only
 * {@code OFFICIALLY_PLACED} records.
 *
 * <p>The group-by figures come from the (tenant-matched) aggregations on the repositories; the placed-per-branch
 * and the CSV's roll/name/branch come from joining the placed students' {@link StudentProfile}s (records carry
 * no branch). Read-only; computed per request.
 */
@Service
public class ReportService {

    private static final PlacementStatus PLACED = PlacementStatus.OFFICIALLY_PLACED;

    private final StudentProfileRepository studentProfileRepository;
    private final PlacementRecordRepository placementRecordRepository;

    public ReportService(StudentProfileRepository studentProfileRepository,
                         PlacementRecordRepository placementRecordRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.placementRecordRepository = placementRecordRepository;
    }

    /** The full placement report for the current admin's tenant. */
    public PlacementReportResponse report() {
        long totalStudents = studentProfileRepository.count();
        List<String> placedIds = placementRecordRepository.findDistinctStudentIdsByStatus(PLACED);

        // placed-per-branch: each distinct placed student's profile, tallied by branch (records carry no branch).
        // A plain HashMap.merge so a null branch (a possible _id group key) doesn't blow up Collectors.groupingBy.
        Map<String, Long> placedPerBranch = new HashMap<>();
        for (StudentProfile p : studentProfileRepository.findByStudentIdIn(placedIds)) {
            placedPerBranch.merge(branchOf(p), 1L, Long::sum);
        }

        List<BranchStat> branchwise = studentProfileRepository.countAllByBranch().stream()
                .map(bc -> {
                    long placed = placedPerBranch.getOrDefault(bc.branch(), 0L);
                    return new BranchStat(bc.branch(), bc.total(), placed, pct(placed, bc.total()));
                })
                // most-placed first; name tiebreaker so equal counts are deterministic across requests
                .sorted(Comparator.comparingLong(BranchStat::placedStudents).reversed()
                        .thenComparing(s -> String.valueOf(s.branch())))
                .toList();

        List<CompanyStat> companywise = placementRecordRepository.countByCompanyForStatus(PLACED).stream()
                .map(cc -> new CompanyStat(cc.company(), cc.placements()))
                .sorted(Comparator.comparingLong(CompanyStat::placements).reversed()
                        .thenComparing(s -> String.valueOf(s.company())))
                .toList();

        OverallStats overall = new OverallStats(totalStudents, placedIds.size(), pct(placedIds.size(), totalStudents));
        return new PlacementReportResponse(overall, branchwise, companywise);
    }

    /**
     * The detailed officially-placed list as CSV (the accreditation artifact): one row per OFFICIALLY_PLACED
     * record, joined to its student's profile for roll/name/branch. RFC-4180-style escaping.
     */
    public String exportCsv() {
        List<PlacementRecord> records = placementRecordRepository.findByStatus(PLACED);
        Map<String, StudentProfile> profiles = studentProfileRepository
                .findByStudentIdIn(records.stream().map(PlacementRecord::getStudentId).toList()).stream()
                .collect(Collectors.toMap(StudentProfile::getStudentId, Function.identity(), (a, b) -> a));

        StringBuilder sb = new StringBuilder("rollNumber,name,branch,company,ctc,role,joiningDate\n");
        for (PlacementRecord r : records) {
            StudentProfile p = profiles.get(r.getStudentId());
            sb.append(row(
                    p == null ? null : p.getRollNumber(),
                    nameOf(p),
                    branchOf(p),
                    r.getCompany(),
                    r.getCtc() == null ? null : r.getCtc().toString(),
                    r.getRole(),
                    dateOnly(r.getJoiningDate())));
        }
        return sb.toString();
    }

    // ── internals ──

    /** Percentage to one decimal; 0.0 when the denominator is zero (never divides by zero). */
    private static double pct(long placed, long total) {
        return total == 0 ? 0.0 : Math.round(placed * 1000.0 / total) / 10.0;
    }

    /** Null-safe branch (the embedded {@code academic} sub-doc) — defends the report/CSV against a 500. */
    private static String branchOf(StudentProfile p) {
        return p == null || p.getAcademic() == null ? null : p.getAcademic().getBranch();
    }

    /** Null-safe full name (the embedded {@code personal} sub-doc). */
    private static String nameOf(StudentProfile p) {
        return p == null || p.getPersonal() == null ? null : p.getPersonal().getFullName();
    }

    /**
     * Joining date as a plain calendar date (UTC), e.g. {@code 2026-10-21} — the stored value is an
     * {@link Instant}, so a bare {@code toString()} would leak the full machine timestamp
     * ({@code 2026-10-21T06:55:43.641Z}) into the placements CSV column.
     */
    private static String dateOnly(java.time.Instant instant) {
        return instant == null ? null : LocalDate.ofInstant(instant, ZoneOffset.UTC).toString();
    }

    private static String row(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields[i]));
        }
        return sb.append('\n').toString();
    }

    /**
     * RFC-4180 escaping (quote + double inner quotes for {@code , " \n \r}) <b>plus</b> CSV formula-injection
     * neutralization: a field starting with {@code = + - @ \t \r} is prefixed with a {@code '} so a spreadsheet
     * does not evaluate it as a formula when the downloaded report is opened (the company/name/role values are
     * free recruiter/student input). Deliberate deviation from strict RFC-4180.
     */
    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String v = value;
        if ("=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
