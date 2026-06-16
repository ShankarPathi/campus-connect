package com.campusconnect.common.events;

/**
 * One recipient of a {@link DomainEvent} (Story 8.1) — the user to notify plus the rendered {@code title} and
 * {@code message}. The producer (which holds the business context) renders the content; the
 * {@link EventPublisher} just persists it. One {@code Notification} document is written per recipient.
 */
public record NotificationRecipient(String userId, String title, String message) {
}
