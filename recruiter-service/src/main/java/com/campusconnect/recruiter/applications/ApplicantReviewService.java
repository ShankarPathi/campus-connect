package com.campusconnect.recruiter.applications;

import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.file.FileStorageService;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.repository.StudentProfileRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.PageResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recruiter applicant review for a single owned drive (Story 6.1, FR-18 / NFR-3) — strictly read-only.
 *
 * <p>Ownership is the gate: every entry point loads the drive owner-scoped ({@code createdBy = }
 * {@link TenantContext#getUserId()}, tenant-scoped via {@link DriveRepository}) and 404s otherwise, so a
 * recruiter can only ever see applicants to <b>their own</b> drives. Applicant rows carry a
 * {@linkplain ApplicantSummaryResponse minimized} (no-restricted-PII) projection; the résumé is a separate
 * on-demand 15-minute pre-signed URL. No status transition happens here — shortlist/reject is Story 6.2.
 *
 * <p><b>Why search/sort run in memory:</b> name and CGPA live on {@code studentProfiles}, not
 * {@code applications}, so they cannot be sorted/searched in the applications query without an aggregation.
 * The status filter pushes to Mongo (the {@code {tenantId, driveId, status}} index); the profiles are then
 * batch-loaded in one query (no N+1) and the bounded per-drive applicant set is searched/sorted/paginated
 * in memory (the Story 5.6 load-once discipline).
 */
@Service
public class ApplicantReviewService {

    private static final Duration RESUME_URL_TTL = Duration.ofMinutes(15);
    /** Upper bound on a caller-supplied page size — caps the in-memory window and the offset multiply. */
    private static final int MAX_PAGE_SIZE = 100;
    /** Hidden from the default applicant pool — a withdrawn student has exited the funnel (Story 6.1 Decision E). */
    private static final EnumSet<ApplicationStatus> DEFAULT_STATUSES =
            EnumSet.complementOf(EnumSet.of(ApplicationStatus.WITHDRAWN));

    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final FileStorageService fileStorage;

    public ApplicantReviewService(DriveRepository driveRepository,
                                  ApplicationRepository applicationRepository,
                                  StudentProfileRepository studentProfileRepository,
                                  FileStorageService fileStorage) {
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.fileStorage = fileStorage;
    }

    /** The applicants to one of the caller's own drives, filtered/searched/sorted/paged. */
    public PageResponse<ApplicantSummaryResponse> listApplicants(String driveId, ApplicantQuery query) {
        requireMyDrive(driveId);

        List<ApplicationStatus> statuses = (query.status() != null && !query.status().isEmpty())
                ? query.status()
                : List.copyOf(DEFAULT_STATUSES);
        List<Application> applications = applicationRepository.findByDriveIdAndStatusIn(driveId, statuses);

        Map<String, StudentProfile> profiles = loadProfiles(applications);
        List<ApplicantSummaryResponse> rows = applications.stream()
                .map(app -> ApplicantSummaryResponse.of(app, profiles.get(app.getStudentId())))
                .filter(matchesSearch(query.search()))
                .sorted(comparatorFor(query.sortBy(), query.sortDir()))
                .toList();

        int page = query.page() != null && query.page() >= 0 ? query.page() : 0;
        int pageSize = query.pageSize() != null && query.pageSize() > 0
                ? Math.min(query.pageSize(), MAX_PAGE_SIZE) : PageResponse.DEFAULT_PAGE_SIZE;
        // long offset so a huge page can't overflow int → negative subList index (→ 500); clamps to a valid empty tail.
        int from = (int) Math.min((long) page * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return PageResponse.of(rows.subList(from, to), rows.size(), page, pageSize);
    }

    /** A fresh 15-minute pre-signed URL for one applicant's frozen résumé snapshot (owner + drive scoped). */
    public ResumeUrlResponse resumeUrl(String driveId, String applicationId) {
        requireMyDrive(driveId);
        Application app = applicationRepository.findByIdAndDriveId(applicationId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found"));
        String key = app.getResumeSnapshotKey();
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException("Résumé not available for this applicant");
        }
        return new ResumeUrlResponse(fileStorage.presignedGetUrl(key, RESUME_URL_TTL), RESUME_URL_TTL.toSeconds());
    }

    // ── internals ──

    /** Asserts the drive is the caller's own (tenant + owner scoped); 404 otherwise. */
    private void requireMyDrive(String driveId) {
        driveRepository.findByIdAndCreatedBy(driveId, TenantContext.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found"));
    }

    private Map<String, StudentProfile> loadProfiles(List<Application> applications) {
        List<String> studentIds = applications.stream().map(Application::getStudentId).distinct().toList();
        return studentProfileRepository.findByStudentIdIn(studentIds).stream()
                .collect(Collectors.toMap(StudentProfile::getStudentId, Function.identity(), (a, b) -> a));
    }

    private static java.util.function.Predicate<ApplicantSummaryResponse> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return r -> true;
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return r -> contains(r.fullName(), needle) || contains(r.rollNumber(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Sort by {@code appliedAt} (default), {@code cgpa}, or {@code name}, nulls always last. Direction:
     * explicit {@code sortDir} wins; otherwise {@code appliedAt} defaults to descending (newest first) and the
     * others to ascending.
     */
    private static Comparator<ApplicantSummaryResponse> comparatorFor(String sortBy, String sortDir) {
        String field = sortBy == null || sortBy.isBlank() ? "appliedat" : sortBy.trim().toLowerCase();
        boolean asc = sortDir != null && !sortDir.isBlank()
                ? "asc".equalsIgnoreCase(sortDir.trim())
                : !field.equals("appliedat"); // appliedAt → newest first by default; cgpa/name → ascending
        return switch (field) {
            case "cgpa" -> Comparator.comparing(ApplicantSummaryResponse::cgpa, nulls(asc));
            case "name" -> Comparator.comparing((ApplicantSummaryResponse r) -> lower(r.fullName()), nulls(asc));
            default -> Comparator.comparing(ApplicantSummaryResponse::appliedAt, nulls(asc));
        };
    }

    /** Ascending or descending natural order with nulls forced last in <i>both</i> directions. */
    private static <T extends Comparable<? super T>> Comparator<T> nulls(boolean asc) {
        Comparator<T> natural = Comparator.naturalOrder();
        return Comparator.nullsLast(asc ? natural : natural.reversed());
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
