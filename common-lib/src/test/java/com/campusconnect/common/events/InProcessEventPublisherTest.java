package com.campusconnect.common.events;

import com.campusconnect.common.domain.EmailOutbox;
import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.domain.NotificationType;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.EmailOutboxRepository;
import com.campusconnect.common.repository.NotificationRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link InProcessEventPublisher}'s control flow (Story 8.1 + 8.2) — its <b>best-effort</b>
 * per-recipient isolation, the in-app notification write, and the Story-8.2 <b>email-outbox enqueue</b>,
 * exercised with mocked repositories (no Mongo). The real unique-index idempotency (which needs a live index)
 * is covered by the admin-service integration tests.
 */
class InProcessEventPublisherTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailOutboxRepository outbox = mock(EmailOutboxRepository.class);
    private final InProcessEventPublisher publisher = new InProcessEventPublisher(notifications, users, outbox);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static User userWithEmail(String email) {
        User u = new User();
        u.setEmail(email);
        return u;
    }

    // ── notification path (Story 8.1) ──

    @Test
    void publish_writesOneNotificationPerRecipient_withTheEventFields() {
        publisher.publish(DomainEvent.of("OFFER_EXPIRED:o1", NotificationType.OFFER_EXPIRED, List.of(
                new NotificationRecipient("alice", "Offer expired", "Your offer expired."),
                new NotificationRecipient("recruiter-1", "Offer expired", "An offer lapsed."))));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getUserId).containsExactly("alice", "recruiter-1");
        assertThat(captor.getAllValues()).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.OFFER_EXPIRED);
            assertThat(n.getEventId()).isEqualTo("OFFER_EXPIRED:o1");
            assertThat(n.isRead()).isFalse();
        });
    }

    @Test
    void publish_oneRecipientSaveThrows_othersStillAttempted_andNeverThrows() {
        // The second recipient's notification save blows up (a non-duplicate runtime failure).
        when(notifications.save(any())).thenReturn(null).thenThrow(new RuntimeException("mongo blip")).thenReturn(null);

        assertThatCode(() -> publisher.publish(DomainEvent.of("X:1", NotificationType.APPLICATION_SHORTLISTED, List.of(
                new NotificationRecipient("a", "t", "m"),
                new NotificationRecipient("b", "t", "m"),
                new NotificationRecipient("c", "t", "m")))))
                .doesNotThrowAnyException();

        // All three recipients were attempted — one failure did not abort the rest.
        verify(notifications, times(3)).save(any());
    }

    @Test
    void publish_duplicateKey_isSwallowed_neverThrows() {
        doThrow(new DuplicateKeyException("already notified")).when(notifications).save(any());

        assertThatCode(() -> publisher.publish(DomainEvent.of("DUP:1", NotificationType.PROFILE_APPROVED,
                new NotificationRecipient("a", "t", "m")))).doesNotThrowAnyException();
        verify(notifications, times(1)).save(any());
    }

    @Test
    void publish_nullEvent_isNoOp() {
        assertThatCode(() -> publisher.publish(null)).doesNotThrowAnyException();
        verify(notifications, never()).save(any());
        verify(outbox, never()).save(any());
    }

    // ── email-outbox enqueue (Story 8.2) ──

    @Test
    void publish_emailWorthy_enqueuesOutboxRow_withResolvedAddressAndContent() {
        TenantContext.set("t1", "admin-1", "COLLEGE_ADMIN");
        when(users.findById("stud-1")).thenReturn(Optional.of(userWithEmail("stud-1@college.edu")));

        publisher.publish(DomainEvent.of("PROFILE_APPROVED:p1", NotificationType.PROFILE_APPROVED,
                new NotificationRecipient("stud-1", "Profile approved", "Your placement profile is approved.")));

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(outbox, times(1)).save(captor.capture());
        EmailOutbox row = captor.getValue();
        assertThat(row.getToEmail()).isEqualTo("stud-1@college.edu");
        assertThat(row.getSubject()).isEqualTo("Profile approved");
        assertThat(row.getBody()).isEqualTo("Your placement profile is approved.");
        assertThat(row.getEventId()).isEqualTo("PROFILE_APPROVED:p1");
        assertThat(row.getUserId()).isEqualTo("stud-1");
        assertThat(row.getTenantId()).isEqualTo("t1");
    }

    @Test
    void publish_nonEmailWorthyType_writesNotificationButNoEmail() {
        // DRIVE_PUBLISHED is reserved and NOT email-worthy.
        publisher.publish(DomainEvent.of("DRIVE_PUBLISHED:d1", NotificationType.DRIVE_PUBLISHED,
                new NotificationRecipient("stud-1", "New drive", "A drive you're eligible for was published.")));

        verify(notifications, times(1)).save(any());
        verify(outbox, never()).save(any());
        verify(users, never()).findById(any());
    }

    @Test
    void publish_emailWorthy_butNoResolvableEmail_enqueuesNothing_neverThrows() {
        when(users.findById("ghost")).thenReturn(Optional.empty());

        assertThatCode(() -> publisher.publish(DomainEvent.of("OFFER_RELEASED:o9", NotificationType.OFFER_RELEASED,
                new NotificationRecipient("ghost", "Offer", "You have an offer.")))).doesNotThrowAnyException();

        verify(notifications, times(1)).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void publish_emailEnqueueDuplicateKey_isSwallowed_neverThrows() {
        when(users.findById("stud-1")).thenReturn(Optional.of(userWithEmail("stud-1@college.edu")));
        doThrow(new DuplicateKeyException("already queued")).when(outbox).save(any());

        assertThatCode(() -> publisher.publish(DomainEvent.of("OFFER_ACCEPTED:o1", NotificationType.OFFER_ACCEPTED,
                new NotificationRecipient("stud-1", "Accepted", "Accepted.")))).doesNotThrowAnyException();
        verify(outbox, times(1)).save(any());
    }

    @Test
    void publish_emailEnqueueRuntimeFailure_isSwallowed_notificationStillWritten() {
        when(users.findById("stud-1")).thenReturn(Optional.of(userWithEmail("stud-1@college.edu")));
        doThrow(new RuntimeException("outbox blip")).when(outbox).save(any());

        assertThatCode(() -> publisher.publish(DomainEvent.of("PLACEMENT_CONFIRMED:pr1",
                NotificationType.PLACEMENT_CONFIRMED,
                new NotificationRecipient("stud-1", "Placed", "Officially placed.")))).doesNotThrowAnyException();

        verify(notifications, times(1)).save(any()); // notification write is not undone by the email failure
        verify(outbox, times(1)).save(any());
    }
}
