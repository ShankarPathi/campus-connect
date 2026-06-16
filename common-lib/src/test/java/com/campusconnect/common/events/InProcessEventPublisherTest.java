package com.campusconnect.common.events;

import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.domain.NotificationType;
import com.campusconnect.common.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link InProcessEventPublisher}'s control flow (Story 8.1) — its <b>best-effort</b> +
 * per-recipient isolation contract, exercised with a mocked {@link NotificationRepository} (no Mongo). The
 * real unique-index idempotency (which needs a live index) is covered by the admin-service integration test.
 */
class InProcessEventPublisherTest {

    @Test
    void publish_writesOneNotificationPerRecipient_withTheEventFields() {
        NotificationRepository repo = mock(NotificationRepository.class);
        InProcessEventPublisher publisher = new InProcessEventPublisher(repo);

        publisher.publish(DomainEvent.of("OFFER_EXPIRED:o1", NotificationType.OFFER_EXPIRED, List.of(
                new NotificationRecipient("alice", "Offer expired", "Your offer expired."),
                new NotificationRecipient("recruiter-1", "Offer expired", "An offer lapsed."))));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getUserId).containsExactly("alice", "recruiter-1");
        assertThat(captor.getAllValues()).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.OFFER_EXPIRED);
            assertThat(n.getEventId()).isEqualTo("OFFER_EXPIRED:o1");
            assertThat(n.isRead()).isFalse();
        });
    }

    @Test
    void publish_oneRecipientSaveThrows_othersStillAttempted_andNeverThrows() {
        NotificationRepository repo = mock(NotificationRepository.class);
        // The second recipient's save blows up (a non-duplicate runtime failure).
        when(repo.save(any())).thenReturn(null).thenThrow(new RuntimeException("mongo blip")).thenReturn(null);
        InProcessEventPublisher publisher = new InProcessEventPublisher(repo);

        assertThatCode(() -> publisher.publish(DomainEvent.of("X:1", NotificationType.APPLICATION_SHORTLISTED, List.of(
                new NotificationRecipient("a", "t", "m"),
                new NotificationRecipient("b", "t", "m"),
                new NotificationRecipient("c", "t", "m")))))
                .doesNotThrowAnyException();

        // All three recipients were attempted — one failure did not abort the rest.
        verify(repo, times(3)).save(any());
    }

    @Test
    void publish_duplicateKey_isSwallowed_neverThrows() {
        NotificationRepository repo = mock(NotificationRepository.class);
        doThrow(new DuplicateKeyException("already notified")).when(repo).save(any());
        InProcessEventPublisher publisher = new InProcessEventPublisher(repo);

        assertThatCode(() -> publisher.publish(DomainEvent.of("DUP:1", NotificationType.PROFILE_APPROVED,
                new NotificationRecipient("a", "t", "m")))).doesNotThrowAnyException();
        verify(repo, times(1)).save(any());
    }

    @Test
    void publish_nullEvent_isNoOp() {
        NotificationRepository repo = mock(NotificationRepository.class);
        InProcessEventPublisher publisher = new InProcessEventPublisher(repo);

        assertThatCode(() -> publisher.publish(null)).doesNotThrowAnyException();
        verify(repo, times(0)).save(any());
    }
}
