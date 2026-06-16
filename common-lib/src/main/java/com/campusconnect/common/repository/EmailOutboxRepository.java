package com.campusconnect.common.repository;

import com.campusconnect.common.domain.EmailOutbox;
import com.campusconnect.common.domain.EmailStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code emailOutbox} collection (Story 8.2, FR-28/NFR-5). <b>Not</b> tenant-aware (the
 * {@link UserRepository}/{@code OfferRepository} pattern): the {@code email-outbox-flush} job runs with no
 * bound tenant, scans across tenants, and updates rows by {@code _id} — so this is a {@link MongoTemplate}-
 * backed {@code @Repository}, not a {@code TenantAwareRepository}. {@code tenantId} is still stamped at
 * enqueue time (from the producer's {@code TenantContext}), so the unique {@code {tenantId, eventId, userId}}
 * index gives <b>per-tenant</b> idempotency.
 */
@Repository
public class EmailOutboxRepository {

    private final MongoTemplate mongoTemplate;

    public EmailOutboxRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public EmailOutbox save(EmailOutbox row) {
        Instant now = Instant.now();
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        row.setUpdatedAt(now);
        return mongoTemplate.save(row);
    }

    public Optional<EmailOutbox> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, EmailOutbox.class));
    }

    /**
     * Up to {@code limit} <b>sendable</b> rows — {@code status == PENDING} and {@code attempts < maxAttempts} —
     * <b>across all tenants</b>, oldest-first ({@code createdAt} ascending), the flush job's enumeration. A
     * deliberate <b>system, cross-tenant</b> read on the raw {@code mongoTemplate} (the
     * {@code OfferRepository.findExpirable} precedent), because the scheduled job runs with no bound tenant.
     * MUST be invoked solely by the system job, never from a request path. A {@code SENT} or dead-lettered
     * ({@code FAILED}, i.e. {@code attempts >= maxAttempts}) row is excluded, so a successfully-sent email is
     * never re-sent.
     *
     * <p>The {@code limit} bounds the per-tick working set so a large backlog (e.g. after an SMTP outage)
     * cannot load every PENDING row into memory or overrun the 5-minute cadence; the FIFO {@code createdAt}
     * order drains the oldest first, and the remainder is picked up by the next tick.
     */
    public List<EmailOutbox> findSendable(int maxAttempts, int limit) {
        Query query = new Query(Criteria.where("status").is(EmailStatus.PENDING)
                .and("attempts").lt(maxAttempts))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"))
                .limit(limit);
        return mongoTemplate.find(query, EmailOutbox.class);
    }
}
