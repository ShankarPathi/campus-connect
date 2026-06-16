package com.campusconnect.student.offers;

import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationLifecycle;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferLifecycle;
import com.campusconnect.common.domain.OfferStatus;
import com.campusconnect.common.domain.PlacementRecord;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.file.FileStorageService;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.repository.OfferRepository;
import com.campusconnect.common.repository.PlacementRecordRepository;
import com.campusconnect.common.repository.StudentProfileRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The student's offer surface (Story 7.3, FR-24): view a released offer and accept or decline it. The first
 * student write on an offer/application the recruiter created (cross-service, shared DB via common-lib repos)
 * and the first use of the {@code placementRecords} collection.
 *
 * <p>Owner-scoped (the 5.5 withdraw pattern): every entry point loads the offer
 * {@code findByIdAndStudentId(offerId, }{@link TenantContext#getUserId()}{@code )} and 404s otherwise.
 * Accept/decline go through the canonical {@link OfferLifecycle} ({@code PENDING → ACCEPTED|DECLINED}) and
 * {@link ApplicationLifecycle} ({@code OFFER_RELEASED → OFFER_ACCEPTED|OFFER_DECLINED}), both {@code @Version}-
 * safe; precise errors come from the pre-provisioned {@code OFFER_ALREADY_RESPONDED} / {@code OFFER_EXPIRED}
 * codes. <b>Accept</b> additionally creates a {@code PENDING_CONFIRMATION} placement record (one per
 * application) and flags the profile {@code isPlaced}; the official confirmation + audit is Story 7.4.
 */
@Service
public class StudentOfferService {

    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);

    private final OfferRepository offerRepository;
    private final ApplicationRepository applicationRepository;
    private final PlacementRecordRepository placementRecordRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final DriveRepository driveRepository;
    private final FileStorageService fileStorage;

    public StudentOfferService(OfferRepository offerRepository,
                               ApplicationRepository applicationRepository,
                               PlacementRecordRepository placementRecordRepository,
                               StudentProfileRepository studentProfileRepository,
                               DriveRepository driveRepository,
                               FileStorageService fileStorage) {
        this.offerRepository = offerRepository;
        this.applicationRepository = applicationRepository;
        this.placementRecordRepository = placementRecordRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.driveRepository = driveRepository;
        this.fileStorage = fileStorage;
    }

    /** The authenticated student's offers (newest concerns handled client-side; small set). */
    public List<OfferSummaryResponse> list() {
        return offerRepository.findByStudentId(TenantContext.getUserId()).stream()
                .map(OfferSummaryResponse::of)
                .toList();
    }

    /** One of the student's own offers in detail + a fresh 15-minute download URL. */
    public OfferDetailResponse view(String offerId) {
        return detail(requireMyOffer(offerId));
    }

    /**
     * Accept: application {@code OFFER_ACCEPTED} → placement record ({@code PENDING_CONFIRMATION}) → {@code isPlaced}
     * → offer {@code ACCEPTED} <b>last</b> (the commit point). Flipping the offer last (it is the
     * {@code requireRespondable} gate's scan key) means a mid-accept failure leaves the offer {@code PENDING}, so a
     * retry <b>self-heals</b>: the application step is idempotent (skipped if already {@code OFFER_ACCEPTED}) and
     * the placement create is idempotent (skipped if one already exists for the application).
     */
    public OfferDetailResponse accept(String offerId) {
        Offer offer = requireMyOffer(offerId);
        requireRespondable(offer);
        OfferLifecycle.requireTransition(offer.getStatus(), OfferStatus.ACCEPTED); // canonical gate (still PENDING)

        Application app = transitionApplication(offer, ApplicationStatus.OFFER_ACCEPTED);
        createPlacementRecord(offer, app);
        flagProfilePlaced();

        offer.setStatus(OfferStatus.ACCEPTED);
        return detail(offerRepository.save(offer));
    }

    /** Decline: application {@code OFFER_DECLINED} → offer {@code DECLINED} last (commit point; self-healing retry). */
    public OfferDetailResponse decline(String offerId) {
        Offer offer = requireMyOffer(offerId);
        requireRespondable(offer);
        OfferLifecycle.requireTransition(offer.getStatus(), OfferStatus.DECLINED);

        transitionApplication(offer, ApplicationStatus.OFFER_DECLINED);

        offer.setStatus(OfferStatus.DECLINED);
        return detail(offerRepository.save(offer));
    }

    // ── internals ──

    /** Loads the offer owner-and-tenant scoped; another student's / another tenant's / a missing offer → 404. */
    private Offer requireMyOffer(String offerId) {
        return offerRepository.findByIdAndStudentId(offerId, TenantContext.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Offer not found."));
    }

    /**
     * The offer must be live: not already responded ({@code OFFER_ALREADY_RESPONDED}) and not expired — either
     * status {@code EXPIRED}, or still {@code PENDING} but past its acceptance deadline before the 7.2 sweep
     * ({@code OFFER_EXPIRED}). The canonical {@link OfferLifecycle} transition is the final gate after this.
     */
    private void requireRespondable(Offer offer) {
        OfferStatus status = offer.getStatus();
        if (status == OfferStatus.ACCEPTED || status == OfferStatus.DECLINED) {
            throw new BusinessException(ErrorCode.OFFER_ALREADY_RESPONDED,
                    "You have already responded to this offer.");
        }
        Instant deadline = offer.getAcceptanceDeadline();
        if (status == OfferStatus.EXPIRED || (deadline != null && deadline.isBefore(Instant.now()))) {
            throw new BusinessException(ErrorCode.OFFER_EXPIRED, "This offer has expired.");
        }
    }

    /**
     * Transitions the offer's application, owner-scoped (the application is the offer's own student's). Idempotent:
     * if a prior partial accept/decline already moved it to {@code to}, this is a no-op — so a self-healing retry
     * does not throw on the now-terminal application state.
     */
    private Application transitionApplication(Offer offer, ApplicationStatus to) {
        Application app = applicationRepository.findByIdAndStudentId(offer.getApplicationId(), offer.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Application not found."));
        if (app.getStatus() == to) {
            return app; // idempotent: already transitioned by a prior partial run
        }
        ApplicationLifecycle.requireTransition(app.getStatus(), to);
        app.setStatus(to);
        return applicationRepository.save(app);
    }

    /** Idempotent: skips if a placement record already exists for this application (a prior partial run). */
    private void createPlacementRecord(Offer offer, Application app) {
        if (placementRecordRepository.findByApplicationId(offer.getApplicationId()).isPresent()) {
            return;
        }
        Drive drive = driveRepository.findById(app.getDriveId()).orElse(null);
        PlacementRecord record = new PlacementRecord();
        record.setStudentId(offer.getStudentId());
        record.setApplicationId(offer.getApplicationId());
        record.setCompany(drive != null ? drive.getCompanyName() : null);
        record.setCtc(offer.getCtc());
        record.setRole(offer.getRole());
        record.setJoiningDate(offer.getJoiningDate());
        placementRecordRepository.save(record);
    }

    /** Targeted single-field update — never read-modify-writes the whole profile (no concurrent-field clobber). */
    private void flagProfilePlaced() {
        studentProfileRepository.setPlaced(TenantContext.getUserId(), true);
    }

    private OfferDetailResponse detail(Offer offer) {
        return OfferDetailResponse.of(offer, fileStorage.presignedGetUrl(offer.getOfferLetterKey(), DOWNLOAD_TTL));
    }
}
