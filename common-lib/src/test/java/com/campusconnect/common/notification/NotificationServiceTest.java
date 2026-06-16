package com.campusconnect.common.notification;

import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.repository.NotificationRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService} (Story 8.3) — the page-size/-number clamps, the unread-only count
 * argument, and the mark-one owner guard — with a mocked {@link NotificationRepository} (no Mongo). The real
 * queries/sort/index are exercised by the student-service integration test.
 */
class NotificationServiceTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repo);

    @BeforeEach
    void bindUser() {
        TenantContext.set("t1", "stud-1", "STUDENT");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void list_clampsOversizeAndNegativePage() {
        when(repo.findForUser(eq("stud-1"), eq(false), eq(0L), eq(NotificationService.MAX_PAGE_SIZE)))
                .thenReturn(List.of());

        NotificationListResponse res = service.list(false, -5, 9999);

        // page floored to 0, size capped to 100, so skip = 0 and limit = 100
        verify(repo).findForUser("stud-1", false, 0L, NotificationService.MAX_PAGE_SIZE);
        assertThat(res.page()).isZero();
        assertThat(res.size()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
    }

    @Test
    void list_clampsZeroSizeToOne_andComputesSkipFromPage() {
        when(repo.findForUser(eq("stud-1"), eq(false), eq(2L), eq(1))).thenReturn(List.of());

        service.list(false, 2, 0); // size 0 -> 1, skip = page(2) * size(1) = 2

        verify(repo).findForUser("stud-1", false, 2L, 1);
    }

    @Test
    void list_unreadOnly_passesTheFilterToTheCount() {
        when(repo.findForUser(eq("stud-1"), eq(true), eq(0L), eq(NotificationService.DEFAULT_PAGE_SIZE)))
                .thenReturn(List.of());
        when(repo.countForUser("stud-1", true)).thenReturn(3L);

        NotificationListResponse res = service.list(true, 0, NotificationService.DEFAULT_PAGE_SIZE);

        // total uses the unreadOnly filter; with unreadOnly=true both total and unreadCount are the unread count
        assertThat(res.total()).isEqualTo(3L);
        assertThat(res.unreadCount()).isEqualTo(3L);
    }

    @Test
    void markRead_missingOrNotOwned_throws404_andDoesNotMutate() {
        when(repo.findByIdAndUserId("n-x", "stud-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead("n-x"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        verify(repo, never()).markRead(eq("n-x"), eq("stud-1"));
    }

    @Test
    void markRead_owned_marksAndReturnsFreshCount() {
        Notification n = new Notification();
        n.setUserId("stud-1");
        when(repo.findByIdAndUserId("n-1", "stud-1")).thenReturn(Optional.of(n));
        when(repo.countForUser("stud-1", true)).thenReturn(4L);

        UnreadCountResponse res = service.markRead("n-1");

        verify(repo).markRead("n-1", "stud-1");
        assertThat(res.unreadCount()).isEqualTo(4L);
    }

    @Test
    void markAllRead_bulkMarks_andReturnsZeroCount() {
        when(repo.countForUser("stud-1", true)).thenReturn(0L);

        UnreadCountResponse res = service.markAllRead();

        verify(repo).markAllRead("stud-1");
        assertThat(res.unreadCount()).isZero();
    }
}
