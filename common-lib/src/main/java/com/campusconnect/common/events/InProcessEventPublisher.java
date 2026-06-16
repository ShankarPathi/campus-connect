package com.campusconnect.common.events;

import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * The MVP {@link EventPublisher} (architecture §9, Story 8.1) — writes one in-app {@link Notification} per
 * recipient to the shared DB, synchronously, in the producer's tenant context. No message broker (the DB is
 * shared); Kafka is the future swap behind {@link EventPublisher}.
 *
 * <p><b>Idempotent:</b> each notification is inserted through the unique {@code {tenantId, eventId, userId}}
 * index, so a re-emit of the same event collides and is swallowed (a no-op). <b>Best-effort:</b> every
 * per-recipient failure is caught + logged — {@code publish} never throws, so a notification problem can never
 * undo the producer's already-committed state change (the 2.2/3.3 {@code notifyStudent} posture). Email
 * delivery + the {@code emailOutbox} retry are added behind this same call in Story 8.2.
 */
@Component
public class InProcessEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventPublisher.class);

    private final NotificationRepository notificationRepository;

    public InProcessEventPublisher(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null || event.recipients() == null) {
            return;
        }
        for (NotificationRecipient recipient : event.recipients()) {
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
    }
}
