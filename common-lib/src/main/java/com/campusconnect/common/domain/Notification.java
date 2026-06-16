package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * An in-app notification for one user (architecture §5/§9, Story 8.1, FR-28) — written by the
 * {@code EventPublisher} when a lifecycle event fires, read by the Story-8.3 notification surface.
 *
 * <p><b>Idempotency:</b> the unique {@code {tenantId, eventId, userId}} index makes a re-emit of the same
 * logical event ({@code eventId} is deterministic per event) a no-op — a second insert for the same
 * (event, user) collides and the publisher swallows it. The {@code {tenantId, userId, isRead}} index backs
 * the read surface's unread-count + newest-first list (Story 8.3). {@code metadata} is reserved for small
 * event-specific context (e.g. an entity id for the frontend to deep-link); Story 8.1 leaves it empty (the
 * deterministic {@code eventId} already embeds the entity id), and the Story-8.3 read surface populates it as needed.
 */
@Document("notifications")
@CompoundIndexes({
        @CompoundIndex(name = "uniq_tenant_event_user",
                def = "{'tenantId': 1, 'eventId': 1, 'userId': 1}", unique = true),
        @CompoundIndex(name = "idx_tenant_user_read",
                def = "{'tenantId': 1, 'userId': 1, 'isRead': 1}")
})
public class Notification extends TenantAwareDocument {

    private String userId;
    private NotificationType type;
    private String title;
    private String message;
    private boolean isRead = false;
    private String eventId;
    private Map<String, Object> metadata;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
