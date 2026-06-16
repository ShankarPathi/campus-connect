package com.campusconnect.common.notification;

import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.domain.NotificationType;

import java.time.Instant;
import java.util.Map;

/**
 * One notification as the read surface returns it (Story 8.3, FR-28) — the user-facing projection of a
 * {@link Notification} (the internal {@code userId}/{@code eventId} are omitted). {@code metadata} is reserved
 * for the frontend to deep-link (8.1 leaves it empty).
 */
public record NotificationResponse(String id, NotificationType type, String title, String message,
                                   boolean isRead, Instant createdAt, Map<String, Object> metadata) {

    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.isRead(), n.getCreatedAt(), n.getMetadata());
    }
}
