package com.campusconnect.common.events;

/**
 * Publishes a domain event to its side effects (architecture §9, Story 8.1, FR-28). The MVP
 * {@link InProcessEventPublisher} writes in-app {@code notification} documents synchronously to the shared DB;
 * Story 8.2 extends the same call with the async email hand-off + {@code emailOutbox} retry. Because the DB is
 * shared, cross-process delivery needs no message broker — Kafka is the documented future swap behind this
 * interface.
 *
 * <p><b>Best-effort:</b> {@code publish} never throws to the caller — a producer's committed, authoritative
 * state change must not be undone by a notification failure.
 */
public interface EventPublisher {

    /** Persists the in-app notification(s) for the event, idempotently and best-effort. Never throws. */
    void publish(DomainEvent event);
}
