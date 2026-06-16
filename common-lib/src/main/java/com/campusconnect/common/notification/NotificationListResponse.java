package com.campusconnect.common.notification;

import java.util.List;

/**
 * A page of the user's notifications (Story 8.3, FR-28) — {@code items} newest-first, {@code total} matching
 * the (optionally unread-only) filter, {@code unreadCount} always the unread total (so the panel badge is
 * available alongside the list), and the {@code page}/{@code size} echoed back.
 */
public record NotificationListResponse(List<NotificationResponse> items, long total, long unreadCount,
                                       int page, int size) {
}
