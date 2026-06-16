package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Notification;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@code notifications} collection (Story 8.1, FR-28). Extends {@link TenantAwareRepository},
 * so every read/write auto-scopes to the current tenant — the {@code EventPublisher} writes through {@code save}
 * (tenant stamped from {@link com.campusconnect.common.tenancy.TenantContext}). The read-surface queries
 * (unread count, newest-first list, mark-read) land in Story 8.3.
 */
@Repository
public class NotificationRepository extends TenantAwareRepository<Notification> {

    public NotificationRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Notification.class);
    }
}
