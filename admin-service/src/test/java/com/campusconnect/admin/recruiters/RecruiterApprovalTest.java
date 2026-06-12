package com.campusconnect.admin.recruiters;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.repository.RecruiterProfileRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** College-Admin recruiter approval/rejection/list (Story 2.2): decision transitions, notifications, authz, state guard. */
@SpringBootTest
@Testcontainers
class RecruiterApprovalTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @TestConfiguration
    static class RecordingMailConfig {
        @Bean
        @Primary
        RecordingEmailService recordingEmailService() {
            return new RecordingEmailService();
        }
    }

    static class RecordingEmailService implements EmailService {
        record Sent(String to, String subject, String body) {
        }

        final List<Sent> sent = new CopyOnWriteArrayList<>();
        volatile boolean failNext = false;

        @Override
        public void sendVerificationEmail(String toEmail, String verificationLink) {
        }

        @Override
        public void sendEmail(String to, String subject, String body) {
            if (failNext) {
                throw new org.springframework.mail.MailSendException("simulated SMTP failure");
            }
            sent.add(new Sent(to, subject, body));
        }

        void clear() {
            sent.clear();
            failNext = false;
        }
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    JwtService jwtService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RecruiterProfileRepository recruiterProfileRepository;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    RecordingEmailService email;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String TENANT = "tenant-a";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
        email.clear();
    }

    @Test
    void approve_movesToActive_andNotifies() throws Exception {
        String userId = seedRecruiter(TENANT, "hr@acme.com", AccountStatus.PENDING_APPROVAL);

        mockMvc.perform(post("/api/admin/recruiters/{id}/approve", userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken(TENANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.findById(userId).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).to()).isEqualTo("hr@acme.com");
    }

    @Test
    void reject_movesToRejected_storesReason_andNotifiesWithReason() throws Exception {
        String userId = seedRecruiter(TENANT, "hr@acme.com", AccountStatus.PENDING_APPROVAL);

        mockMvc.perform(post("/api/admin/recruiters/{id}/reject", userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken(TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectRecruiterRequest("Company could not be verified"))))
                .andExpect(status().isOk());

        User rejected = userRepository.findById(userId).orElseThrow();
        assertThat(rejected.getAccountStatus()).isEqualTo(AccountStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Company could not be verified");
        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).body()).contains("Company could not be verified");
    }

    @Test
    void reject_withBlankReason_isRejectedWith400() throws Exception {
        String userId = seedRecruiter(TENANT, "hr@acme.com", AccountStatus.PENDING_APPROVAL);

        mockMvc.perform(post("/api/admin/recruiters/{id}/reject", userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken(TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectRecruiterRequest("  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void approve_nonPendingRecruiter_isRejectedWith409() throws Exception {
        String userId = seedRecruiter(TENANT, "active@acme.com", AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/admin/recruiters/{id}/approve", userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken(TENANT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(email.sent).isEmpty(); // the 409 guard precedes any notification
    }

    @Test
    void approve_stillSucceeds_whenNotificationEmailFails() throws Exception {
        String userId = seedRecruiter(TENANT, "hr@acme.com", AccountStatus.PENDING_APPROVAL);
        email.failNext = true; // notification is best-effort — must not fail the decision

        mockMvc.perform(post("/api/admin/recruiters/{id}/approve", userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken(TENANT)))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(userId).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void approve_unknownRecruiter_isRejectedWith404() throws Exception {
        mockMvc.perform(post("/api/admin/recruiters/{id}/approve", "no-such-user")
                        .header(HttpHeaders.AUTHORIZATION, adminToken(TENANT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void nonAdmin_isForbidden403() throws Exception {
        String userId = seedRecruiter(TENANT, "hr@acme.com", AccountStatus.PENDING_APPROVAL);

        mockMvc.perform(post("/api/admin/recruiters/{id}/approve", userId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.issueAccessToken("stud-1", Role.STUDENT, TENANT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_isUnauthorized401() throws Exception {
        String userId = seedRecruiter(TENANT, "hr@acme.com", AccountStatus.PENDING_APPROVAL);

        mockMvc.perform(post("/api/admin/recruiters/{id}/approve", userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_returnsOnlyThisTenantsPendingRecruiters_withCompanyDetails() throws Exception {
        seedRecruiter(TENANT, "p1@acme.com", AccountStatus.PENDING_APPROVAL);
        seedRecruiter(TENANT, "p2@acme.com", AccountStatus.PENDING_APPROVAL);
        seedRecruiter(TENANT, "active@acme.com", AccountStatus.ACTIVE);   // different status
        seedRecruiter("tenant-b", "other@b.com", AccountStatus.PENDING_APPROVAL); // different tenant

        mockMvc.perform(get("/api/admin/recruiters").param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, adminToken(TENANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].companyName").exists());
    }

    // ── helpers ──

    private String seedRecruiter(String tenantId, String emailAddr, AccountStatus statusValue) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(emailAddr);
        u.setPasswordHash("hash");
        u.setRole(Role.RECRUITER);
        u.setAccountStatus(statusValue);
        String userId = userRepository.save(u).getId();

        RecruiterProfile p = new RecruiterProfile();
        p.setTenantId(tenantId);
        p.setUserId(userId);
        p.setCompanyName("Acme Corp");
        recruiterProfileRepository.save(p);
        return userId;
    }

    private String adminToken(String tenantId) {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.COLLEGE_ADMIN, tenantId);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
