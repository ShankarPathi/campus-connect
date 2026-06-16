package com.campusconnect.common.notification;

import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.NotificationRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The notification read surface (Story 8.3, FR-28) — unread count, newest-first paginated list, mark-one,
 * mark-all. A shared {@code common-lib} {@code @Service} (component-scanned into every service, the
 * {@code AuditService}/{@code EventPublisher} precedent), so the deferred recruiter/admin portal controllers
 * reuse it unchanged; only the student-service controller exists today (architecture §3).
 *
 * <p><b>Always owner-scoped:</b> every operation runs against {@link TenantContext#getUserId()} (and the
 * tenant, via {@link NotificationRepository}) — a user sees and marks <b>only their own</b> notifications (the
 * 5.5/5.6 posture). Marks are targeted/bulk field updates and idempotent. No business rules beyond scoping;
 * no new {@code ErrorCode} (mark-one on a foreign/missing id reuses {@code NOT_FOUND}).
 */
@Service
public class NotificationService {

    /** Hard cap on a requested page size — defends against an unbounded list request. */
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** The caller's unread count. */
    public long unreadCount() {
        return notificationRepository.countForUser(userId(), true);
    }

    /** A newest-first page of the caller's notifications (optionally unread-only) + totals. */
    public NotificationListResponse list(boolean unreadOnly, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long skip = (long) safePage * safeSize; // long math — a huge page must not overflow int into a negative skip
        String uid = userId();

        List<NotificationResponse> items = notificationRepository
                .findForUser(uid, unreadOnly, skip, safeSize).stream()
                .map(NotificationResponse::of)
                .toList();
        long total = notificationRepository.countForUser(uid, unreadOnly);
        // when unreadOnly, total already IS the unread count — avoid a redundant second count
        long unread = unreadOnly ? total : notificationRepository.countForUser(uid, true);
        return new NotificationListResponse(items, total, unread, safePage, safeSize);
    }

    /**
     * Marks one of the caller's own notifications read; a missing or not-owned id → 404 {@code NOT_FOUND}
     * (the 5.5 withdraw guard). Already-read is an idempotent no-op. Returns the caller's fresh unread count.
     */
    public UnreadCountResponse markRead(String notificationId) {
        String uid = userId();
        notificationRepository.findByIdAndUserId(notificationId, uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Notification not found."));
        notificationRepository.markRead(notificationId, uid);
        return new UnreadCountResponse(notificationRepository.countForUser(uid, true));
    }

    /** Marks all the caller's unread notifications read (bulk, idempotent). Returns the fresh unread count (0). */
    public UnreadCountResponse markAllRead() {
        String uid = userId();
        notificationRepository.markAllRead(uid);
        return new UnreadCountResponse(notificationRepository.countForUser(uid, true));
    }

    private static String userId() {
        return TenantContext.getUserId();
    }
}
