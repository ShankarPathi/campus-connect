package com.campusconnect.common.notification;

/** The user's unread-notification count (Story 8.3, FR-28) — the panel badge + the mark-read responses. */
public record UnreadCountResponse(long unreadCount) {
}
