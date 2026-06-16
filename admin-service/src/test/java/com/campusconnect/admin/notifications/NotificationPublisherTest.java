package com.campusconnect.admin.notifications;

import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.domain.NotificationType;
import com.campusconnect.common.events.DomainEvent;
import com.campusconnect.common.events.EventPublisher;
import com.campusconnect.common.events.NotificationRecipient;
import com.campusconnect.common.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The in-process EventPublisher (Story 8.1, FR-28): writes one notification per recipient, idempotently by
 * {@code eventId} (the real unique {@code {tenantId, eventId, userId}} index, auto-created here), best-effort.
 * Tested in a service module because the index needs Spring's auto-index-creation (common-lib has no app context).
 */
@SpringBootTest
@Testcontainers
class NotificationPublisherTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired EventPublisher eventPublisher;
    @Autowired MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), Notification.class);
        TenantContext.set("t1", "admin-1", "COLLEGE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publish_singleRecipient_writesOneNotification() {
        eventPublisher.publish(DomainEvent.of("PROFILE_APPROVED:p1", NotificationType.PROFILE_APPROVED,
                new NotificationRecipient("alice", "Profile approved", "Your profile is approved.")));

        List<Notification> all = forUser("alice");
        assertThat(all).hasSize(1);
        Notification n = all.get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.PROFILE_APPROVED);
        assertThat(n.getTitle()).isEqualTo("Profile approved");
        assertThat(n.getMessage()).isEqualTo("Your profile is approved.");
        assertThat(n.isRead()).isFalse();
        assertThat(n.getEventId()).isEqualTo("PROFILE_APPROVED:p1");
        assertThat(n.getTenantId()).isEqualTo("t1");
    }

    @Test
    void publish_multipleRecipients_writesOnePerRecipient() {
        eventPublisher.publish(DomainEvent.of("OFFER_EXPIRED:o1", NotificationType.OFFER_EXPIRED, List.of(
                new NotificationRecipient("alice", "Offer expired", "Your offer expired."),
                new NotificationRecipient("recruiter-1", "Offer expired", "An offer lapsed."))));

        assertThat(forUser("alice")).hasSize(1);
        assertThat(forUser("recruiter-1")).hasSize(1);
    }

    @Test
    void publish_sameEventTwice_isIdempotent_noDuplicate() {
        DomainEvent event = DomainEvent.of("APPLICANT_SELECTED:a1", NotificationType.APPLICANT_SELECTED,
                new NotificationRecipient("alice", "Selected", "You've been selected."));

        eventPublisher.publish(event);
        eventPublisher.publish(event); // re-emit — must no-op via the unique {tenantId, eventId, userId} index

        assertThat(forUser("alice")).hasSize(1);
    }

    @Test
    void publish_differentTenants_doNotCollideOnSameEventId() {
        DomainEvent event = DomainEvent.of("OFFER_RELEASED:o9", NotificationType.OFFER_RELEASED,
                new NotificationRecipient("alice", "Offer", "You have an offer."));
        eventPublisher.publish(event);                       // t1
        TenantContext.set("t2", "admin-2", "COLLEGE_ADMIN");
        eventPublisher.publish(event);                       // t2 — same eventId+user, different tenant → distinct row

        assertThat(mongoTemplate.find(new Query(Criteria.where("userId").is("alice")), Notification.class)).hasSize(2);
    }

    @Test
    void publish_emptyRecipients_isNoOp_andNeverThrows() {
        assertThatCode(() -> eventPublisher.publish(
                DomainEvent.of("X:1", NotificationType.OFFER_RELEASED, List.of())))
                .doesNotThrowAnyException();
        assertThat(mongoTemplate.findAll(Notification.class)).isEmpty();
    }

    private List<Notification> forUser(String userId) {
        return mongoTemplate.find(new Query(Criteria.where("userId").is(userId)), Notification.class);
    }
}
