package com.campusconnect.admin.jobs;

import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferStatus;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offer-expiry scheduled job (Story 7.2, FR-23, AC1–9). Invokes {@link OfferExpiryService#expireOverdueOffers}
 * directly with a controlled {@code now} (the standard way to test {@code @Scheduled} logic — never relying on
 * the scheduler firing). Seeds {@code offers}/{@code applications} straight through {@code mongoTemplate} with
 * explicit {@code tenantId}, since a job runs with no request/tenant context.
 */
@SpringBootTest
@Testcontainers
class OfferExpiryJobTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired OfferExpiryService offerExpiryService;
    @Autowired MongoTemplate mongoTemplate;

    /** Per-application discriminator so seeded applications never collide on uniq_tenant_student_drive. */
    private int seq;

    static final Instant NOW = Instant.parse("2026-06-16T00:00:00Z");
    static final Instant PAST = Instant.parse("2026-06-15T00:00:00Z"); // before NOW → overdue
    static final Instant FUTURE = Instant.parse("2026-06-30T00:00:00Z"); // after NOW → not yet due

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        mongoTemplate.remove(new Query(), Offer.class);
        mongoTemplate.remove(new Query(), Application.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void overduePendingOffer_isExpired_appTransitioned_audited() {
        String appId = seedApplication("t1", ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer("t1", appId, "alice", OfferStatus.PENDING, PAST);

        OfferExpiryResult result = offerExpiryService.expireOverdueOffers(NOW);

        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(mongoTemplate.findById(offerId, Offer.class).getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.OFFER_EXPIRED);

        AuditLog audit = mongoTemplate.findOne(
                new Query(Criteria.where("entityId").is(appId).and("action").is("OFFER_EXPIRED")), AuditLog.class);
        assertThat(audit).isNotNull();
        assertThat(audit.getActor()).isEqualTo("SYSTEM");
        assertThat(audit.getEntityType()).isEqualTo("Application");
        assertThat(audit.getOldValue()).isEqualTo("status=OFFER_RELEASED");
        assertThat(audit.getNewValue()).isEqualTo("status=OFFER_EXPIRED");
    }

    @Test
    void futureDeadlinePendingOffer_isUntouched() {
        String appId = seedApplication("t1", ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer("t1", appId, "alice", OfferStatus.PENDING, FUTURE);

        OfferExpiryResult result = offerExpiryService.expireOverdueOffers(NOW);

        assertThat(result.expiredCount()).isZero();
        assertThat(mongoTemplate.findById(offerId, Offer.class).getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.OFFER_RELEASED);
        assertThat(auditCount(appId)).isZero();
    }

    @Test
    void alreadyExpiredOffer_isUntouched() {
        String appId = seedApplication("t1", ApplicationStatus.OFFER_EXPIRED);
        seedOffer("t1", appId, "alice", OfferStatus.EXPIRED, PAST);

        OfferExpiryResult result = offerExpiryService.expireOverdueOffers(NOW);

        assertThat(result.expiredCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(auditCount(appId)).isZero();
    }

    @Test
    void acceptedOrDeclinedOffer_isUntouched() {
        String acceptedApp = seedApplication("t1", ApplicationStatus.OFFER_ACCEPTED);
        seedOffer("t1", acceptedApp, "alice", OfferStatus.ACCEPTED, PAST);
        String declinedApp = seedApplication("t1", ApplicationStatus.OFFER_DECLINED);
        seedOffer("t1", declinedApp, "bob", OfferStatus.DECLINED, PAST);

        OfferExpiryResult result = offerExpiryService.expireOverdueOffers(NOW);

        assertThat(result.expiredCount()).isZero();
        assertThat(mongoTemplate.findById(acceptedApp, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.OFFER_ACCEPTED);
        assertThat(mongoTemplate.findById(declinedApp, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.OFFER_DECLINED);
    }

    @Test
    void offersAcrossTwoTenants_bothExpire_underTheirOwnScope() {
        String app1 = seedApplication("t1", ApplicationStatus.OFFER_RELEASED);
        seedOffer("t1", app1, "alice", OfferStatus.PENDING, PAST);
        String app2 = seedApplication("t2", ApplicationStatus.OFFER_RELEASED);
        seedOffer("t2", app2, "carol", OfferStatus.PENDING, PAST);

        OfferExpiryResult result = offerExpiryService.expireOverdueOffers(NOW);

        assertThat(result.expiredCount()).isEqualTo(2);
        assertThat(mongoTemplate.findById(app1, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.OFFER_EXPIRED);
        assertThat(mongoTemplate.findById(app2, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.OFFER_EXPIRED);
        // each audit row landed in its own tenant
        assertThat(mongoTemplate.findOne(new Query(Criteria.where("entityId").is(app1)), AuditLog.class).getTenantId())
                .isEqualTo("t1");
        assertThat(mongoTemplate.findOne(new Query(Criteria.where("entityId").is(app2)), AuditLog.class).getTenantId())
                .isEqualTo("t2");
    }

    @Test
    void runningTwice_isIdempotent_noSecondEffect() {
        String appId = seedApplication("t1", ApplicationStatus.OFFER_RELEASED);
        seedOffer("t1", appId, "alice", OfferStatus.PENDING, PAST);

        OfferExpiryResult first = offerExpiryService.expireOverdueOffers(NOW);
        OfferExpiryResult second = offerExpiryService.expireOverdueOffers(NOW);

        assertThat(first.expiredCount()).isEqualTo(1);
        assertThat(second.expiredCount()).isZero();
        assertThat(second.failedCount()).isZero();
        // exactly one audit row despite two runs
        assertThat(auditCount(appId)).isEqualTo(1);
    }

    @Test
    void oneBadOffer_doesNotAbortTheBatch() {
        String healthyApp = seedApplication("t1", ApplicationStatus.OFFER_RELEASED);
        String healthyOffer = seedOffer("t1", healthyApp, "alice", OfferStatus.PENDING, PAST);
        // a broken offer whose application does not exist → its per-offer processing fails
        String brokenOffer = seedOffer("t1", "missing-application-id", "ghost", OfferStatus.PENDING, PAST);

        OfferExpiryResult result = offerExpiryService.expireOverdueOffers(NOW);

        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        // the healthy offer still fully processed
        assertThat(mongoTemplate.findById(healthyOffer, Offer.class).getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(mongoTemplate.findById(healthyApp, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.OFFER_EXPIRED);
        // the broken offer is left untouched (PENDING) — the app-first/offer-last order writes nothing on failure
        assertThat(mongoTemplate.findById(brokenOffer, Offer.class).getStatus()).isEqualTo(OfferStatus.PENDING);
    }

    // ── helpers ──

    private long auditCount(String applicationId) {
        return mongoTemplate.count(
                new Query(Criteria.where("entityId").is(applicationId).and("action").is("OFFER_EXPIRED")), AuditLog.class);
    }

    private String seedApplication(String tenant, ApplicationStatus status) {
        int n = ++seq; // unique per application → no uniq_tenant_student_drive collision
        Application a = new Application();
        a.setTenantId(tenant);
        a.setStudentId("student-" + tenant + "-" + n);
        a.setDriveId("drive-" + tenant + "-" + n);
        a.setStatus(status);
        a.setAppliedAt(Instant.parse("2026-06-01T00:00:00Z"));
        return mongoTemplate.save(a).getId();
    }

    private String seedOffer(String tenant, String applicationId, String studentId,
                             OfferStatus status, Instant acceptanceDeadline) {
        Offer o = new Offer();
        o.setTenantId(tenant);
        o.setApplicationId(applicationId);
        o.setStudentId(studentId);
        o.setOfferLetterKey("offers/" + tenant + "/" + applicationId + "/x.pdf");
        o.setRole("SDE-1");
        o.setCtc(12.5);
        o.setJoiningDate(Instant.parse("2030-07-01T00:00:00Z"));
        o.setAcceptanceDeadline(acceptanceDeadline);
        o.setStatus(status);
        return mongoTemplate.save(o).getId();
    }
}
