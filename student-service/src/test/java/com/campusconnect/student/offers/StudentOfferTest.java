package com.campusconnect.student.offers;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferStatus;
import com.campusconnect.common.domain.PlacementRecord;
import com.campusconnect.common.domain.PlacementStatus;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.file.FileStorageService;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Student accept/decline of an offer (Story 7.3, FR-24): view + accept (→ placement record + isPlaced) +
 * decline, owner-scoped 404s, the pre-provisioned OFFER_ALREADY_RESPONDED / OFFER_EXPIRED guards, and
 * STUDENT-only authz. A @Primary stub FileStorageService serves a canned download URL (no MinIO).
 */
@SpringBootTest
@Testcontainers
class StudentOfferTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @TestConfiguration
    static class StubStorageConfig {
        @Bean @Primary FileStorageService stubFileStorage() {
            return new FileStorageService() {
                @Override public void put(String key, byte[] bytes, String contentType) { }
                @Override public String presignedGetUrl(String key, Duration ttl) {
                    return "https://signed.example/" + key + "?ttl=" + ttl.toSeconds();
                }
            };
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    String tenantId;
    String studentId;
    String driveId;

    static final Instant FUTURE = Instant.parse("2030-06-01T00:00:00Z");
    static final Instant PAST = Instant.parse("2020-06-01T00:00:00Z");
    static final Instant JOINING = Instant.parse("2030-07-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), Application.class);
        mongoTemplate.remove(new Query(), Offer.class);
        mongoTemplate.remove(new Query(), PlacementRecord.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mongoTemplate.remove(new Query(), com.campusconnect.common.domain.Notification.class);
        tenantId = seedTenant("vignan");
        studentId = seedUser(tenantId, "s@v.edu", Role.STUDENT);
        seedProfile(tenantId, studentId);
        driveId = seedDrive(tenantId);
    }

    // ── accept ──

    @Test
    void accept_createsPlacementRecord_transitionsApp_flagsPlaced() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(tenantId, studentId, appId, OfferStatus.PENDING, FUTURE);

        mockMvc.perform(post("/api/student/offers/{id}/accept", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.offerLetterUrl").exists())
                .andExpect(jsonPath("$.data.offerLetterKey").doesNotExist());

        assertThat(mongoTemplate.findById(offerId, Offer.class).getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.OFFER_ACCEPTED);

        PlacementRecord pr = mongoTemplate.findOne(new Query(Criteria.where("applicationId").is(appId)), PlacementRecord.class);
        assertThat(pr).isNotNull();
        assertThat(pr.getStatus()).isEqualTo(PlacementStatus.PENDING_CONFIRMATION);
        assertThat(pr.getStudentId()).isEqualTo(studentId);
        assertThat(pr.getCompany()).isEqualTo("Acme Corp");
        assertThat(pr.getCtc()).isEqualTo(12.5);
        assertThat(pr.getRole()).isEqualTo("SDE-1");
        assertThat(pr.getJoiningDate()).isEqualTo(JOINING);

        assertThat(myProfile().isPlaced()).isTrue();

        // Story 8.1: accepting notifies the recruiter (the drive's creator, "rec-1") in-app.
        assertThat(mongoTemplate.find(new Query(Criteria.where("userId").is("rec-1")),
                com.campusconnect.common.domain.Notification.class))
                .extracting(n -> n.getType().name()).containsExactly("OFFER_ACCEPTED");
    }

    @Test
    void secondAccept_is409_alreadyResponded_oneePlacementRecord() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(tenantId, studentId, appId, OfferStatus.PENDING, FUTURE);
        mockMvc.perform(post("/api/student/offers/{id}/accept", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/student/offers/{id}/accept", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OFFER_ALREADY_RESPONDED"));

        assertThat(mongoTemplate.find(new Query(Criteria.where("applicationId").is(appId)), PlacementRecord.class)).hasSize(1);
    }

    // ── decline ──

    @Test
    void decline_transitionsOfferAndApp_noPlacement_notPlaced() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(tenantId, studentId, appId, OfferStatus.PENDING, FUTURE);

        mockMvc.perform(post("/api/student/offers/{id}/decline", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));

        assertThat(mongoTemplate.findById(offerId, Offer.class).getStatus()).isEqualTo(OfferStatus.DECLINED);
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.OFFER_DECLINED);
        assertThat(mongoTemplate.find(new Query(Criteria.where("applicationId").is(appId)), PlacementRecord.class)).isEmpty();
        assertThat(myProfile().isPlaced()).isFalse();
    }

    // ── view / list ──

    @Test
    void view_returnsDetail_withUrl_noKey() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(tenantId, studentId, appId, OfferStatus.PENDING, FUTURE);

        mockMvc.perform(get("/api/student/offers/{id}", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(offerId))
                .andExpect(jsonPath("$.data.role").value("SDE-1"))
                .andExpect(jsonPath("$.data.offerLetterUrl", org.hamcrest.Matchers.startsWith("https://signed.example/offers/")))
                .andExpect(jsonPath("$.data.offerLetterKey").doesNotExist());
    }

    @Test
    void list_returnsOnlyMyOffers() throws Exception {
        String myApp = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_RELEASED);
        seedOffer(tenantId, studentId, myApp, OfferStatus.PENDING, FUTURE);
        String otherStudent = seedUser(tenantId, "other@v.edu", Role.STUDENT);
        String otherApp = seedApplication(tenantId, otherStudent, ApplicationStatus.OFFER_RELEASED);
        seedOffer(tenantId, otherStudent, otherApp, OfferStatus.PENDING, FUTURE);

        mockMvc.perform(get("/api/student/offers").header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].applicationId").value(myApp));
    }

    // ── expiry guards ──

    @Test
    void accept_expiredStatusOffer_is409_offerExpired() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(tenantId, studentId, appId, OfferStatus.EXPIRED, FUTURE);

        mockMvc.perform(post("/api/student/offers/{id}/accept", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OFFER_EXPIRED"));
    }

    @Test
    void accept_pastDeadlinePendingOffer_is409_offerExpired() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(tenantId, studentId, appId, OfferStatus.PENDING, PAST); // not yet swept by 7.2

        mockMvc.perform(post("/api/student/offers/{id}/accept", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OFFER_EXPIRED"));

        assertThat(mongoTemplate.findById(offerId, Offer.class).getStatus()).isEqualTo(OfferStatus.PENDING);
    }

    @Test
    void decline_alreadyDeclined_is409_alreadyResponded() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_DECLINED);
        String offerId = seedOffer(tenantId, studentId, appId, OfferStatus.DECLINED, FUTURE);

        mockMvc.perform(post("/api/student/offers/{id}/decline", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OFFER_ALREADY_RESPONDED"));
    }

    // ── ownership / tenancy 404 ──

    @Test
    void accept_anotherStudentsOffer_is404() throws Exception {
        String otherStudent = seedUser(tenantId, "other@v.edu", Role.STUDENT);
        String appId = seedApplication(tenantId, otherStudent, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(tenantId, otherStudent, appId, OfferStatus.PENDING, FUTURE);

        mockMvc.perform(post("/api/student/offers/{id}/accept", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(mongoTemplate.findById(offerId, Offer.class).getStatus()).isEqualTo(OfferStatus.PENDING);
    }

    @Test
    void accept_crossTenantOffer_is404() throws Exception {
        String otherTenant = seedTenant("other");
        String appId = seedApplication(otherTenant, studentId, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(otherTenant, studentId, appId, OfferStatus.PENDING, FUTURE);

        mockMvc.perform(post("/api/student/offers/{id}/accept", offerId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isNotFound());
    }

    // ── authz ──

    @Test
    void accept_asRecruiter_is403() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.OFFER_RELEASED);
        String offerId = seedOffer(tenantId, studentId, appId, OfferStatus.PENDING, FUTURE);
        mockMvc.perform(post("/api/student/offers/{id}/accept", offerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken(studentId, Role.RECRUITER, tenantId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void view_noToken_is401() throws Exception {
        mockMvc.perform(get("/api/student/offers/{id}", "any"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    private StudentProfile myProfile() {
        return mongoTemplate.findOne(new Query(Criteria.where("studentId").is(studentId)), StudentProfile.class);
    }

    private String token(String userId) {
        return "Bearer " + jwtService.issueAccessToken(userId, Role.STUDENT, tenantId);
    }

    private String seedOffer(String tid, String sid, String appId, OfferStatus status, Instant deadline) {
        Offer o = new Offer();
        o.setTenantId(tid);
        o.setStudentId(sid);
        o.setApplicationId(appId);
        o.setOfferLetterKey("offers/" + tid + "/" + appId + "/x.pdf");
        o.setRole("SDE-1");
        o.setCtc(12.5);
        o.setJoiningDate(JOINING);
        o.setAcceptanceDeadline(deadline);
        o.setStatus(status);
        return mongoTemplate.save(o).getId();
    }

    private String seedApplication(String tid, String sid, ApplicationStatus status) {
        Application a = new Application();
        a.setTenantId(tid);
        a.setStudentId(sid);
        a.setDriveId(driveId);
        a.setStatus(status);
        a.setAppliedAt(Instant.parse("2026-06-01T00:00:00Z"));
        return mongoTemplate.save(a).getId();
    }

    private void seedProfile(String tid, String sid) {
        StudentProfile p = new StudentProfile();
        p.setTenantId(tid);
        p.setStudentId(sid);
        p.setPlaced(false);
        mongoTemplate.save(p);
    }

    private String seedDrive(String tid) {
        Drive d = new Drive();
        d.setTenantId(tid);
        d.setCreatedBy("rec-1");
        d.setCompanyName("Acme Corp");
        d.setRole("SDE-1");
        d.setStatus(DriveStatus.PUBLISHED);
        return mongoTemplate.save(d).getId();
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(List.of("CSE", "ECE"));
        t.setBatches(List.of("2026"));
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedUser(String tid, String emailAddr, Role role) {
        User u = new User();
        u.setTenantId(tid);
        u.setEmail(emailAddr.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(u).getId();
    }
}
