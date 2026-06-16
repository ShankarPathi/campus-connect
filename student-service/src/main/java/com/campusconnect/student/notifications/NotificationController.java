package com.campusconnect.student.notifications;

import com.campusconnect.common.notification.NotificationListResponse;
import com.campusconnect.common.notification.NotificationService;
import com.campusconnect.common.notification.UnreadCountResponse;
import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated student's notification panel (Story 8.3, FR-28). Base path
 * {@code /api/student/notifications} requires a valid token (shared chain); {@code @PreAuthorize} narrows it to
 * the STUDENT role (active-status enforced by the Story 2.5 filter). Thin — it only adapts HTTP to the shared
 * {@link NotificationService}, which owner-scopes every operation to the caller. The recruiter/admin portals
 * reuse the same service via their own controllers (deferred).
 */
@RestController
@RequestMapping("/api/student/notifications")
@PreAuthorize("hasRole('STUDENT')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Newest-first page of my notifications (optionally unread-only) + totals + unread count. */
    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.list(unreadOnly, page, size));
    }

    /** My unread count — the panel badge. */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(new UnreadCountResponse(notificationService.unreadCount()));
    }

    /** Mark one of my notifications read (404 if not mine); returns the new unread count. */
    @PostMapping("/{id}/read")
    public ApiResponse<UnreadCountResponse> markRead(@PathVariable String id) {
        return ApiResponse.ok(notificationService.markRead(id), "Marked read.");
    }

    /** Mark all my notifications read; returns the new unread count (0). */
    @PostMapping("/read-all")
    public ApiResponse<UnreadCountResponse> markAllRead() {
        return ApiResponse.ok(notificationService.markAllRead(), "All notifications marked read.");
    }
}
