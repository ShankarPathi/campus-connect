package com.campusconnect.common.events;

import com.campusconnect.common.domain.NotificationType;

import java.util.List;
import java.util.Map;

/**
 * A published domain event (architecture §9, Story 8.1) — what a producer hands to the {@link EventPublisher}
 * after a committed state change. Carries a <b>deterministic</b> {@code eventId} (stable for the same logical
 * event, e.g. {@code "PROFILE_APPROVED:" + profileId}, so a re-emit is a no-op), the notification {@code type},
 * the resolved {@code recipients} (each with its rendered title/message), and optional {@code metadata}.
 *
 * <p>The producer resolves recipients + renders content; the publisher persists. This keeps {@link EventPublisher}
 * a thin seam — clean for the Story-8.2 email hand-off and a future Kafka swap behind the same interface.
 */
public record DomainEvent(String eventId, NotificationType type, List<NotificationRecipient> recipients,
                          Map<String, Object> metadata) {

    /** A single-recipient event with no metadata. */
    public static DomainEvent of(String eventId, NotificationType type, NotificationRecipient recipient) {
        return new DomainEvent(eventId, type, List.of(recipient), Map.of());
    }

    /** A multi-recipient event with no metadata. */
    public static DomainEvent of(String eventId, NotificationType type, List<NotificationRecipient> recipients) {
        return new DomainEvent(eventId, type, recipients, Map.of());
    }
}
