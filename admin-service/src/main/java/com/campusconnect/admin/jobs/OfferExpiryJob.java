package com.campusconnect.admin.jobs;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The daily offer-expiry scheduled job (Story 7.2, FR-23; architecture §10) — the codebase's first
 * {@code @Scheduled} job, hosted in admin-service. Thin by design: it only supplies {@code now} and delegates
 * to {@link OfferExpiryService}, which holds all the logic (and is unit-tested directly, without the scheduler).
 * The cron is config-driven ({@code app.jobs.offer-expiry.cron}, default daily at 02:00).
 */
@Component
public class OfferExpiryJob {

    private final OfferExpiryService offerExpiryService;

    public OfferExpiryJob(OfferExpiryService offerExpiryService) {
        this.offerExpiryService = offerExpiryService;
    }

    @Scheduled(cron = "${app.jobs.offer-expiry.cron:0 0 2 * * *}")
    @SchedulerLock(name = "offer-expiry", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void expireOverdueOffers() {
        offerExpiryService.expireOverdueOffers(Instant.now());
    }
}
