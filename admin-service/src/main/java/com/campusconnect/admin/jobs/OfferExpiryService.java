package com.campusconnect.admin.jobs;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationLifecycle;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferLifecycle;
import com.campusconnect.common.domain.OfferStatus;
import com.campusconnect.common.repository.ApplicationRepository;
import com.campusconnect.common.repository.OfferRepository;
import com.campusconnect.common.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Expires overdue offers (Story 7.2, FR-23) — the logic behind the {@link OfferExpiryJob} scheduled job,
 * separated so it is testable with a controlled {@code now} (never relying on the scheduler firing).
 *
 * <p>Marks every {@code PENDING} offer past its acceptance deadline {@code EXPIRED} and moves its application
 * {@code OFFER_RELEASED → OFFER_EXPIRED}, both through the canonical lifecycles ({@link OfferLifecycle} /
 * {@link ApplicationLifecycle}), {@code @Version}-safe and audited. <b>Idempotent</b>: the scan predicate is
 * {@code status == PENDING && acceptanceDeadline <= now}, so an already-expired (or accepted/declined) offer
 * is never re-processed. <b>Resilient</b>: each offer is handled in its own {@code try/catch}, so one bad
 * offer never aborts the batch.
 *
 * <p><b>No principal, cross-tenant:</b> the enumeration is the system-scoped {@link OfferRepository#findExpirable}
 * (the only un-tenant-filtered read); each offer is then processed under its own tenant context
 * ({@link TenantContext#set} with a {@code SYSTEM} actor, {@link TenantContext#clear} in a {@code finally}),
 * so the writes stay tenant-scoped and the audit row is attributed to {@code SYSTEM}.
 */
@Service
public class OfferExpiryService {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryService.class);
    /** The actor recorded for system-initiated changes — the job has no authenticated principal. */
    static final String SYSTEM_ACTOR = "SYSTEM";

    private final OfferRepository offerRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditService auditService;

    public OfferExpiryService(OfferRepository offerRepository,
                              ApplicationRepository applicationRepository,
                              AuditService auditService) {
        this.offerRepository = offerRepository;
        this.applicationRepository = applicationRepository;
        this.auditService = auditService;
    }

    /** Expires every {@code PENDING} offer whose acceptance deadline is at/before {@code now}, across all tenants. */
    public OfferExpiryResult expireOverdueOffers(Instant now) {
        List<Offer> expirable = offerRepository.findExpirable(now);
        int expired = 0;
        int failed = 0;
        for (Offer offer : expirable) {
            try {
                TenantContext.set(offer.getTenantId(), SYSTEM_ACTOR, null);
                expireOne(offer);
                expired++;
            } catch (Exception e) {
                failed++;
                log.warn("offer-expiry: skipped offer {} (application {}): {}",
                        offer.getId(), offer.getApplicationId(), e.toString());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("offer-expiry: {} expired, {} failed (of {} overdue)", expired, failed, expirable.size());
        return new OfferExpiryResult(expired, failed);
    }

    /**
     * Application-transition + audit <b>first</b>, then flip the offer {@code EXPIRED} <b>last</b>, all under the
     * offer's bound tenant context. Ordering the offer write last means a mid-method failure leaves the offer
     * {@code PENDING} — so it is re-scanned and <b>self-heals</b> on the next run, and the authoritative
     * application state is never stranded {@code OFFER_RELEASED} behind an already-expired offer. <b>Idempotent
     * on re-entry:</b> if a prior partial run already moved the application to {@code OFFER_EXPIRED}, the
     * application transition + audit are skipped (no duplicate row) and only the offer is finished.
     */
    private void expireOne(Offer offer) {
        Application app = applicationRepository.findById(offer.getApplicationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Application " + offer.getApplicationId() + " not found for offer " + offer.getId()));

        if (app.getStatus() != ApplicationStatus.OFFER_EXPIRED) {
            ApplicationStatus from = app.getStatus();
            ApplicationLifecycle.requireTransition(from, ApplicationStatus.OFFER_EXPIRED);
            app.setStatus(ApplicationStatus.OFFER_EXPIRED);
            applicationRepository.save(app);
            auditService.record(AuditAction.OFFER_EXPIRED, "Application", offer.getApplicationId(),
                    "status=" + from, "status=" + ApplicationStatus.OFFER_EXPIRED);
        }

        OfferLifecycle.requireTransition(offer.getStatus(), OfferStatus.EXPIRED);
        offer.setStatus(OfferStatus.EXPIRED);
        offerRepository.save(offer);
    }
}
