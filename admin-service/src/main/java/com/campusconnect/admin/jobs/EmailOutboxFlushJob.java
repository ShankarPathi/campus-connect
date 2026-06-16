package com.campusconnect.admin.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The every-5-min email-outbox-flush scheduled job (Story 8.2, FR-28/NFR-5; architecture §10) — hosted in
 * admin-service (the {@code @EnableScheduling} jobs host), the codebase's second {@code @Scheduled} job. Thin
 * by design: it only triggers and delegates to {@link EmailOutboxFlushService}, which holds all the logic (and
 * is unit-tested directly, without the scheduler). The cron is config-driven
 * ({@code app.jobs.email-outbox-flush.cron}, default every 5 minutes).
 */
@Component
public class EmailOutboxFlushJob {

    private final EmailOutboxFlushService emailOutboxFlushService;

    public EmailOutboxFlushJob(EmailOutboxFlushService emailOutboxFlushService) {
        this.emailOutboxFlushService = emailOutboxFlushService;
    }

    @Scheduled(cron = "${app.jobs.email-outbox-flush.cron:0 */5 * * * *}")
    public void flushOutbox() {
        emailOutboxFlushService.flush();
    }
}
