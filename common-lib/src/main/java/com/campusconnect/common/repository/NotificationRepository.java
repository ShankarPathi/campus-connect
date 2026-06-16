package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Notification;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code notifications} collection (Story 8.1, FR-28). Extends {@link TenantAwareRepository},
 * so every read/write auto-scopes to the current tenant — the {@code EventPublisher} writes through {@code save}
 * (tenant stamped from {@link com.campusconnect.common.tenancy.TenantContext}).
 *
 * <p>Story 8.3 adds the <b>read-surface</b> queries, all additionally scoped to a {@code userId} (the
 * authenticated recipient — a user only ever sees/marks their own): a newest-first paginated list, an unread
 * count, an owner guard, and targeted/bulk mark-read updates (the {@code StudentProfileRepository.setPlaced} /
 * {@code setLockedForTenant} pattern — a single field update, never a read-modify-write, naturally idempotent).
 */
@Repository
public class NotificationRepository extends TenantAwareRepository<Notification> {

    public NotificationRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Notification.class);
    }

    /** The user's notifications, newest-first ({@code createdAt} desc), optionally unread-only, paginated. Tenant-scoped. */
    public List<Notification> findForUser(String userId, boolean unreadOnly, long skip, int limit) {
        Query query = userQuery(userId, unreadOnly)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(withTenant(query), type);
    }

    /** Count of the user's notifications (optionally unread-only) in the current tenant. */
    public long countForUser(String userId, boolean unreadOnly) {
        return mongoTemplate.count(withTenant(userQuery(userId, unreadOnly)), type);
    }

    /** One notification scoped to its owner (tenant + {@code userId}) — the mark-one 404 guard. */
    public Optional<Notification> findByIdAndUserId(String id, String userId) {
        return findById(id).filter(n -> userId.equals(n.getUserId()));
    }

    /**
     * Marks one of the user's own notifications read — a <b>targeted</b> {@code updateFirst} (only {@code isRead}
     * + {@code updatedAt}), tenant + {@code userId} scoped. Returns the modified count (0 if missing / not the
     * user's / already read). Idempotent.
     */
    public long markRead(String id, String userId) {
        Query query = new Query(Criteria.where("_id").is(id).and("userId").is(userId)).addCriteria(tenantCriteria());
        Update update = Update.update("isRead", true).set("updatedAt", Instant.now());
        return mongoTemplate.updateFirst(query, update, type).getModifiedCount();
    }

    /**
     * Marks <b>all</b> the user's unread notifications read in one bulk {@code updateMulti}, tenant + {@code userId}
     * scoped. Returns the number flipped (0 if none were unread). Idempotent.
     */
    public long markAllRead(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId).and("isRead").is(false)).addCriteria(tenantCriteria());
        Update update = Update.update("isRead", true).set("updatedAt", Instant.now());
        return mongoTemplate.updateMulti(query, update, type).getModifiedCount();
    }

    /** {@code userId} (+ {@code isRead=false} when {@code unreadOnly}) criterion — the tenant criterion is added by callers. */
    private static Query userQuery(String userId, boolean unreadOnly) {
        Criteria criteria = Criteria.where("userId").is(userId);
        if (unreadOnly) {
            criteria = criteria.and("isRead").is(false);
        }
        return new Query(criteria);
    }
}
