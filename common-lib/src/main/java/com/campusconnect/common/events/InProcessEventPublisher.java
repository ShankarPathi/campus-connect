package com.campusconnect.common.events;

import com.campusconnect.common.domain.EmailOutbox;
import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.domain.NotificationType;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.EmailOutboxRepository;
import com.campusconnect.common.repository.NotificationRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The MVP {@link EventPublisher} (architecture §9, Story 8.1 + 8.2) — writes one in-app {@link Notification}
 * per recipient to the shared DB, synchronously, in the producer's tenant context, and (Story 8.2) enqueues
 * one {@link EmailOutbox} row per recipient for the <b>email-worthy</b> types. No message broker (the DB is
 * shared); Kafka is the future swap behind {@link EventPublisher}.
 *
 * <p><b>Idempotent:</b> both the notification and the outbox row are inserted through a unique
 * {@code {tenantId, eventId, userId}} index, so a re-emit of the same event collides and is swallowed (a
 * no-op) — no duplicate notification and no duplicate email. <b>Best-effort:</b> every per-recipient failure
 * (notification write or email enqueue) is caught + logged independently — {@code publish} never throws, so a
 * notification/email problem can never undo the producer's already-committed state change (the 2.2/3.3
 * {@code notifyStudent} posture).
 *
 * <p><b>Transactional outbox (Story 8.2):</b> {@code publish} only <b>enqueues</b> the email (a fast DB write,
 * no SMTP on the hot path); the every-5-min {@code email-outbox-flush} job (admin-service) is the sole SMTP
 * sender and retries with a dead-letter cap. The recipient address is resolved here (once, at enqueue) and
 * stored on the row, so the flush job is self-contained.
 */
@Component
public class InProcessEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventPublisher.class);

    /**
     * The notification types that also warrant an email (Story 8.2) — every type a producer currently emits.
     * The reserved-but-unproduced {@code DRIVE_PUBLISHED} / {@code ROUND_RESCHEDULED} are excluded until their
     * audience-resolved fan-outs ship.
     */
    private static final Set<NotificationType> EMAIL_WORTHY = EnumSet.of(
            NotificationType.PROFILE_APPROVED,
            NotificationType.PROFILE_REJECTED,
            NotificationType.APPLICATION_SHORTLISTED,
            NotificationType.APPLICATION_REJECTED,
            NotificationType.ROUND_RESULT,
            NotificationType.APPLICANT_SELECTED,
            NotificationType.OFFER_RELEASED,
            NotificationType.OFFER_EXPIRED,
            NotificationType.OFFER_ACCEPTED,
            NotificationType.OFFER_DECLINED,
            NotificationType.PLACEMENT_CONFIRMED);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailOutboxRepository emailOutboxRepository;

    public InProcessEventPublisher(NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   EmailOutboxRepository emailOutboxRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailOutboxRepository = emailOutboxRepository;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null || event.recipients() == null) {
            return;
        }
        boolean emailWorthy = EMAIL_WORTHY.contains(event.type());
        for (NotificationRecipient recipient : event.recipients()) {
            writeNotification(event, recipient);
            if (emailWorthy) {
                enqueueEmail(event, recipient);
            }
        }
    }

    /** One in-app notification per recipient (Story 8.1). Best-effort: a failure here never aborts the rest. */
    private void writeNotification(DomainEvent event, NotificationRecipient recipient) {
        try {
            Notification notification = new Notification();
            notification.setUserId(recipient.userId());
            notification.setType(event.type());
            notification.setTitle(recipient.title());
            notification.setMessage(recipient.message());
            notification.setEventId(event.eventId());
            notification.setMetadata(event.metadata());
            notificationRepository.save(notification);
        } catch (DuplicateKeyException alreadyNotified) {
            // Idempotent re-emit: this (event, user) notification already exists — a no-op.
        } catch (RuntimeException ex) {
            // Best-effort: a notification failure must never fail the producer's committed action.
            log.warn("notification publish failed for user {} (event {}, type {}): {}",
                    recipient.userId(), event.eventId(), event.type(), ex.toString());
        }
    }

    /**
     * One {@link EmailOutbox} row per recipient for the email-worthy types (Story 8.2) — shielded separately
     * from the notification write (neither undoes the other). Resolves the address once; a recipient with no
     * resolvable email enqueues nothing. The unique index makes a re-emit a no-op.
     */
    private void enqueueEmail(DomainEvent event, NotificationRecipient recipient) {
        try {
            Optional<User> user = userRepository.findById(recipient.userId());
            if (user.isEmpty() || user.get().getEmail() == null || user.get().getEmail().isBlank()) {
                return; // no address to send to — best-effort, never throws
            }
            EmailOutbox row = new EmailOutbox();
            row.setTenantId(TenantContext.getTenantId());
            row.setToEmail(user.get().getEmail());
            row.setSubject(recipient.title());
            row.setBody(recipient.message());
            row.setEventId(event.eventId());
            row.setUserId(recipient.userId());
            emailOutboxRepository.save(row);
        } catch (DuplicateKeyException alreadyQueued) {
            // Idempotent re-emit: this (event, user) email is already queued/sent — a no-op.
        } catch (RuntimeException ex) {
            // Best-effort: an email-enqueue failure must never fail the producer's committed action.
            log.warn("email enqueue failed for user {} (event {}, type {}): {}",
                    recipient.userId(), event.eventId(), event.type(), ex.toString());
        }
    }
}
