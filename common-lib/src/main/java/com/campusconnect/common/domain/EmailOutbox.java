package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A durable outbound email (architecture §9/§10, Story 8.2, FR-28/NFR-5) — written by the
 * {@code EventPublisher} when an <b>email-worthy</b> lifecycle event fires, drained by the every-5-min
 * {@code email-outbox-flush} job (admin-service) which is the <b>sole</b> SMTP sender.
 *
 * <p><b>Transactional outbox:</b> the producer enqueues a {@code PENDING} row (a fast, reliable DB write —
 * no SMTP on the hot path); the job sends it, then marks it {@code SENT}. <b>Idempotent:</b> the unique
 * {@code {tenantId, eventId, userId}} index (the same key as {@code notifications}) makes a re-emit of the
 * same logical event a no-op — a second enqueue collides and the publisher swallows it, so no second email.
 * Delivery is <b>at-least-once</b>: a crash between the SMTP send and the {@code SENT} write leaves the row
 * {@code PENDING} and it re-sends next tick (a provider-side idempotency key for exactly-once is Epic 10).
 *
 * <p>The recipient address is resolved + stored as {@code toEmail} at enqueue time, so the row is
 * self-contained (the flush job needs no {@code UserRepository}). {@code subject}/{@code body} are the
 * title/message the producer already rendered for the in-app notification.
 */
@Document("emailOutbox")
@CompoundIndexes({
        @CompoundIndex(name = "uniq_tenant_event_user_email",
                def = "{'tenantId': 1, 'eventId': 1, 'userId': 1}", unique = true),
        @CompoundIndex(name = "idx_status", def = "{'status': 1}")
})
public class EmailOutbox extends TenantAwareDocument {

    private String toEmail;
    private String subject;
    private String body;
    private String eventId;
    private String userId;
    private EmailStatus status = EmailStatus.PENDING;
    private int attempts = 0;
    /** The last send failure's message — diagnosis only; null until a send fails. */
    private String lastError;
    /** When the row was successfully sent; null until {@code SENT}. */
    private Instant sentAt;

    public String getToEmail() {
        return toEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public EmailStatus getStatus() {
        return status;
    }

    public void setStatus(EmailStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
