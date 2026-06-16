package com.campusconnect.admin.notifications;

import com.campusconnect.admin.jobs.EmailOutboxFlushResult;
import com.campusconnect.admin.jobs.EmailOutboxFlushService;
import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.EmailOutbox;
import com.campusconnect.common.domain.EmailStatus;
import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.domain.NotificationType;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.events.DomainEvent;
import com.campusconnect.common.events.EventPublisher;
import com.campusconnect.common.events.NotificationRecipient;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end email outbox path (Story 8.2, FR-28/NFR-5): an email-worthy {@code publish} enqueues a durable
 * {@code PENDING} row (address resolved from the user), and the flush job is the sole sender — marking it
 * {@code SENT} and never re-sending. Idempotent across a re-publish + re-flush; per-tenant independent. Run in
 * a service module because the real unique {@code {tenantId, eventId, userId}} index needs auto-index-creation
 * and the flush job uses the live {@code findSendable} query (common-lib has no app context).
 */
@SpringBootTest
@Testcontainers
class EmailOutboxFlushIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @TestConfiguration
    static class RecordingMailConfig {
        @Bean @Primary RecordingEmailService recordingEmailService() {
            return new RecordingEmailService();
        }
    }

    static class RecordingEmailService implements EmailService {
        record Sent(String to, String subject, String body) {
        }
        final List<Sent> sent = new CopyOnWriteArrayList<>();
        @Override public void sendVerificationEmail(String toEmail, String link) {
        }
        @Override public void sendEmail(String to, String subject, String body) {
            sent.add(new Sent(to, subject, body));
        }
        void clear() {
            sent.clear();
        }
    }

    @Autowired EventPublisher eventPublisher;
    @Autowired EmailOutboxFlushService flushService;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired RecordingEmailService email;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), EmailOutbox.class);
        mongoTemplate.remove(new Query(), Notification.class);
        mongoTemplate.remove(new Query(), User.class);
        email.clear();
        TenantContext.set("t1", "admin-1", "COLLEGE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void seedUser(String id, String tenantId, String emailAddr) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setEmail(emailAddr);
        u.setPasswordHash("hash");
        u.setRole(Role.STUDENT);
        u.setAccountStatus(AccountStatus.ACTIVE);
        mongoTemplate.save(u);
    }

    private List<EmailOutbox> outboxFor(String userId) {
        return mongoTemplate.find(new Query(Criteria.where("userId").is(userId)), EmailOutbox.class);
    }

    @Test
    void publishThenFlush_enqueuesPending_thenSendsOnce_andNeverResends() {
        seedUser("stud-1", "t1", "stud-1@college.edu");

        eventPublisher.publish(DomainEvent.of("OFFER_RELEASED:o1", NotificationType.OFFER_RELEASED,
                new NotificationRecipient("stud-1", "You have an offer", "An offer was released to you.")));

        // Enqueued PENDING with the resolved address + the in-app notification — but NOT yet sent.
        List<EmailOutbox> queued = outboxFor("stud-1");
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(queued.get(0).getToEmail()).isEqualTo("stud-1@college.edu");
        assertThat(queued.get(0).getSubject()).isEqualTo("You have an offer");
        assertThat(mongoTemplate.find(new Query(Criteria.where("userId").is("stud-1")), Notification.class)).hasSize(1);
        assertThat(email.sent).isEmpty();

        // Flush sends exactly once and marks the row SENT.
        EmailOutboxFlushResult first = flushService.flush();
        assertThat(first).isEqualTo(new EmailOutboxFlushResult(1, 0));
        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).to()).isEqualTo("stud-1@college.edu");
        EmailOutbox afterSend = outboxFor("stud-1").get(0);
        assertThat(afterSend.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(afterSend.getSentAt()).isNotNull();

        // A second flush re-sends nothing — a SENT row is excluded by findSendable.
        EmailOutboxFlushResult second = flushService.flush();
        assertThat(second).isEqualTo(new EmailOutboxFlushResult(0, 0));
        assertThat(email.sent).hasSize(1);
    }

    @Test
    void rePublishingSameEvent_enqueuesNoSecondRow_soNoSecondEmail() {
        seedUser("stud-1", "t1", "stud-1@college.edu");
        DomainEvent event = DomainEvent.of("PLACEMENT_CONFIRMED:pr1", NotificationType.PLACEMENT_CONFIRMED,
                new NotificationRecipient("stud-1", "Placed", "You are officially placed."));

        eventPublisher.publish(event);
        eventPublisher.publish(event); // re-emit — collides on the unique {tenantId, eventId, userId} index

        assertThat(outboxFor("stud-1")).hasSize(1);

        flushService.flush();
        assertThat(email.sent).hasSize(1); // exactly one email despite the double publish
    }

    @Test
    void rowsAreFlushedAcrossTenants_independently() {
        seedUser("stud-1", "t1", "stud-1@college.edu");
        seedUser("stud-2", "t2", "stud-2@other.edu");

        eventPublisher.publish(DomainEvent.of("APPLICANT_SELECTED:a1", NotificationType.APPLICANT_SELECTED,
                new NotificationRecipient("stud-1", "Selected", "You were selected.")));   // t1
        TenantContext.set("t2", "admin-2", "COLLEGE_ADMIN");
        eventPublisher.publish(DomainEvent.of("APPLICANT_SELECTED:a2", NotificationType.APPLICANT_SELECTED,
                new NotificationRecipient("stud-2", "Selected", "You were selected.")));   // t2

        assertThat(outboxFor("stud-1").get(0).getTenantId()).isEqualTo("t1");
        assertThat(outboxFor("stud-2").get(0).getTenantId()).isEqualTo("t2");

        // The flush job is system-scoped — it drains both tenants in one pass.
        EmailOutboxFlushResult result = flushService.flush();
        assertThat(result).isEqualTo(new EmailOutboxFlushResult(2, 0));
        assertThat(email.sent).hasSize(2);
    }

    @Test
    void noResolvableEmail_enqueuesNothing() {
        // No user seeded for "ghost" → the publisher cannot resolve an address → no outbox row, no throw.
        eventPublisher.publish(DomainEvent.of("OFFER_ACCEPTED:o5", NotificationType.OFFER_ACCEPTED,
                new NotificationRecipient("ghost", "Accepted", "Accepted.")));

        assertThat(outboxFor("ghost")).isEmpty();
        assertThat(flushService.flush()).isEqualTo(new EmailOutboxFlushResult(0, 0));
    }
}
