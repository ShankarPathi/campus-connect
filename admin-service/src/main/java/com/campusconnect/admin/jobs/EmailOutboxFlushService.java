package com.campusconnect.admin.jobs;

import com.campusconnect.common.domain.EmailOutbox;
import com.campusconnect.common.domain.EmailStatus;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.repository.EmailOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Drains the {@code emailOutbox} (Story 8.2, FR-28/NFR-5) — the logic behind the {@link EmailOutboxFlushJob}
 * scheduled job, separated so it is testable without the scheduler.
 *
 * <p>Sends every <b>sendable</b> row ({@code PENDING}, under the attempt cap) via the existing
 * {@link EmailService} (Mailpit dev / Brevo prod) and marks it {@code SENT}. <b>Idempotent</b>: only
 * {@code PENDING} rows under the cap are picked ({@link EmailOutboxRepository#findSendable}), so a {@code SENT}
 * row is never re-sent. <b>Retry + dead-letter</b>: a failed send increments {@code attempts} and records
 * {@code lastError}; the row stays {@code PENDING} (retried next tick) until {@code attempts >= MAX_ATTEMPTS},
 * when it flips to {@code FAILED} (a dead-letter, retained, never re-swept). <b>Resilient</b>: each row is
 * handled in its own {@code try/catch}, so one bad row never aborts the batch.
 *
 * <p><b>No principal, cross-tenant:</b> the enumeration is the system-scoped {@link EmailOutboxRepository}
 * (the {@code OfferExpiryService} precedent); sends and by-{@code _id} status updates need no
 * {@code TenantContext}. Delivery is <b>at-least-once</b> — a crash between {@code send} and the {@code SENT}
 * write re-sends the row next tick (a provider idempotency key for exactly-once is Epic 10).
 */
@Service
public class EmailOutboxFlushService {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxFlushService.class);
    /** How many sends are attempted before a row is dead-lettered to {@code FAILED}. */
    static final int MAX_ATTEMPTS = 5;
    /**
     * Max rows drained per tick — bounds the working set so a large backlog (e.g. after an SMTP outage) cannot
     * load every PENDING row into memory or overrun the 5-minute cadence. A full batch means more remain; the
     * next tick (or, under a heavy backlog, several) drains the rest, oldest-first.
     */
    static final int BATCH_SIZE = 200;

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailService emailService;

    public EmailOutboxFlushService(EmailOutboxRepository emailOutboxRepository, EmailService emailService) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailService = emailService;
    }

    /** Sends every sendable outbox row; returns the run summary. Never throws (each row is isolated). */
    public EmailOutboxFlushResult flush() {
        List<EmailOutbox> sendable = emailOutboxRepository.findSendable(MAX_ATTEMPTS, BATCH_SIZE);
        int sent = 0;
        int failed = 0;
        for (EmailOutbox row : sendable) {
            if (sendOne(row)) {
                sent++;
            } else {
                failed++;
            }
        }
        if (sent > 0 || failed > 0) {
            log.info("email-outbox-flush: {} sent, {} failed (of {} sendable)", sent, failed, sendable.size());
        }
        if (sendable.size() == BATCH_SIZE) {
            // A full batch means more PENDING rows remain — drained oldest-first on subsequent ticks (no silent cap).
            log.info("email-outbox-flush: batch full ({} rows) — more pending, will continue next tick", BATCH_SIZE);
        }
        return new EmailOutboxFlushResult(sent, failed);
    }

    /** Sends one row, recording success/failure on it. Returns true on a successful send. */
    private boolean sendOne(EmailOutbox row) {
        try {
            emailService.sendEmail(row.getToEmail(), row.getSubject(), row.getBody());
            row.setStatus(EmailStatus.SENT);
            row.setSentAt(Instant.now());
            emailOutboxRepository.save(row);
            return true;
        } catch (RuntimeException ex) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(ex.toString());
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.setStatus(EmailStatus.FAILED); // dead-letter — exhausted retries
                log.warn("email-outbox row {} dead-lettered after {} attempts (to {}): {}",
                        row.getId(), row.getAttempts(), row.getToEmail(), ex.toString());
            }
            emailOutboxRepository.save(row);
            return false;
        }
    }
}
