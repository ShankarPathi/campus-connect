package com.campusconnect.recruiter.offers;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationLifecycle;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.NotificationType;
import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.events.DomainEvent;
import com.campusconnect.common.events.EventPublisher;
import com.campusconnect.common.events.NotificationRecipient;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.file.FileStorageService;
import com.campusconnect.common.file.PdfValidation;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.DriveRepository;
import com.campusconnect.common.repository.OfferRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Releases a job offer to a {@code SELECTED} student (Story 7.1, FR-23) — the first use of the {@code offers}
 * collection and the recruiter-side of placement. Reuses the Epic-6 discipline: the drive is loaded
 * owner-scoped (404 otherwise) and the application drive-scoped (404 otherwise), the status change goes
 * through the canonical {@link ApplicationLifecycle} ({@code SELECTED → OFFER_RELEASED}, {@code @Version}-safe),
 * and the action is audited.
 *
 * <p><b>One offer per application</b> (Decision C): a {@code findByApplicationId} pre-check gives a clean 409
 * {@code CONFLICT}; the unique {@code {tenantId, applicationId}} index is the concurrency backstop (a racing
 * second insert → {@code DuplicateKeyException} → the global handler's 409).
 *
 * <p><b>Write order</b> (Decision D, non-transactional — Mongo standalone): validate + store the PDF, then run
 * the {@code @Version}-guarded application transition, then insert the offer. The PDF goes first (a failure
 * there changes no DB state); the guarded transition runs before the offer insert so a concurrent double
 * release fails at the second caller's transition (the app is no longer {@code SELECTED}) rather than racing
 * the unique index. The residual (offer insert fails after the app save) joins the accepted atomicity cluster.
 */
@Service
public class OfferService {

    /** The recruiter verify URL is short-lived, like the résumé preview (Story 3.2/6.1). */
    private static final Duration OFFER_URL_TTL = Duration.ofMinutes(15);

    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final OfferRepository offerRepository;
    private final FileStorageService fileStorage;
    private final AuditService auditService;
    private final long maxSizeBytes;
    private final EventPublisher eventPublisher;

    public OfferService(DriveRepository driveRepository,
                        ApplicationRepository applicationRepository,
                        OfferRepository offerRepository,
                        FileStorageService fileStorage,
                        AuditService auditService,
                        EventPublisher eventPublisher,
                        @Value("${app.offer.max-size-bytes:5242880}") long maxSizeBytes) {
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.offerRepository = offerRepository;
        this.fileStorage = fileStorage;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.maxSizeBytes = maxSizeBytes;
    }

    /** Releases an offer for one {@code SELECTED} application of the caller's own drive. */
    public OfferResponse release(String driveId, String applicationId, ReleaseOfferRequest data, MultipartFile file) {
        requireMyDrive(driveId);
        Application app = applicationRepository.findByIdAndDriveId(applicationId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found"));

        // One offer per application — clean pre-check (the unique index is the concurrency backstop).
        if (offerRepository.findByApplicationId(applicationId).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "An offer has already been released for this application.");
        }

        // Cross-field: you accept before you join.
        if (!data.acceptanceDeadline().isBefore(data.joiningDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The acceptance deadline must be before the joining date.");
        }

        // Validate the PDF by content + size (NOT filename/Content-Type), then store it privately.
        byte[] bytes = readBytes(file);
        if (!PdfValidation.isPdf(bytes)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The offer letter must be a PDF.");
        }
        if (bytes.length > maxSizeBytes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The offer letter exceeds the maximum allowed size.");
        }
        String key = "offers/" + TenantContext.requireTenantId() + "/" + applicationId + "/" + UUID.randomUUID() + ".pdf";
        fileStorage.put(key, bytes, "application/pdf");

        // Guarded, @Version-safe application transition (SELECTED → OFFER_RELEASED) — runs before the offer insert.
        ApplicationStatus from = app.getStatus();
        ApplicationLifecycle.requireTransition(from, ApplicationStatus.OFFER_RELEASED);
        app.setStatus(ApplicationStatus.OFFER_RELEASED);
        applicationRepository.save(app);

        // Create the offer (PENDING). A racing duplicate → DuplicateKeyException → global 409.
        Offer offer = new Offer();
        offer.setApplicationId(applicationId);
        offer.setStudentId(app.getStudentId());
        offer.setOfferLetterKey(key);
        offer.setRole(data.role());
        offer.setCtc(data.ctc());
        offer.setJoiningDate(data.joiningDate());
        offer.setAcceptanceDeadline(data.acceptanceDeadline());
        Offer saved = offerRepository.save(offer);

        auditService.record(AuditAction.OFFER_RELEASED, "Application", applicationId,
                "status=" + from, "status=" + ApplicationStatus.OFFER_RELEASED);

        eventPublisher.publish(DomainEvent.of("OFFER_RELEASED:" + saved.getId(), NotificationType.OFFER_RELEASED,
                new NotificationRecipient(app.getStudentId(),
                        "Offer released", "You have received an offer (" + data.role() + "). Review and respond before the deadline.")));

        return OfferResponse.of(saved, fileStorage.presignedGetUrl(key, OFFER_URL_TTL));
    }

    /** Asserts the drive is the caller's own (tenant + owner scoped); 404 otherwise (the Epic-6 gate). */
    private void requireMyDrive(String driveId) {
        driveRepository.findByIdAndCreatedBy(driveId, TenantContext.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found"));
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No offer letter was uploaded.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Could not read the uploaded offer letter.");
        }
    }
}
