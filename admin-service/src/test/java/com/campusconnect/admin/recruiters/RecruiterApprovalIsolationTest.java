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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR-1 cross-tenant isolation for the first authenticated tenant-scoped write endpoints (retro
 * action item #4 / Story 1.7 deferred). A COLLEGE_ADMIN of tenant A must NOT be able to approve,
 * reject, or list tenant B's recruiter — and CAN act on its own tenant's recruiter.
 */
@SpringBootTest
@Testcontainers
class RecruiterApprovalIsolationTest {

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
        EmailService email() {
            return new EmailService() {
                @Override
                public void sendVerificationEmail(String toEmail, String verificationLink) {
                }

                @Override
                public void sendEmail(String to, String subject, String body) {
                }
            };
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

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
    }

    @Test
    void adminOfTenantA_cannotApproveTenantBsRecruiter() throws Exception {
        String recruiterB = seedRecruiter("tenant-b", "hr@b.com");

        mockMvc.perform(post("/api/admin/recruiters/{id}/approve", recruiterB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken("tenant-a")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // unchanged — A's admin could not touch B's recruiter
        assertThat(userRepository.findById(recruiterB).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.PENDING_APPROVAL);
    }

    @Test
    void adminOfTenantA_cannotRejectTenantBsRecruiter() throws Exception {
        String recruiterB = seedRecruiter("tenant-b", "hr@b.com");

        mockMvc.perform(post("/api/admin/recruiters/{id}/reject", recruiterB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken("tenant-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectRecruiterRequest("nope"))))
                .andExpect(status().isNotFound());

        User untouched = userRepository.findById(recruiterB).orElseThrow();
        assertThat(untouched.getRejectionReason()).isNull();
        assertThat(untouched.getAccountStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL); // status unchanged too
    }

    @Test
    void adminList_neverIncludesAnotherTenantsRecruiters() throws Exception {
        seedRecruiter("tenant-a", "mine@a.com");
        seedRecruiter("tenant-b", "theirs@b.com");

        mockMvc.perform(get("/api/admin/recruiters").param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, adminToken("tenant-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("mine@a.com"));
    }

    @Test
    void adminOfTenantA_canApproveItsOwnRecruiter() throws Exception {
        String recruiterA = seedRecruiter("tenant-a", "hr@a.com");

        mockMvc.perform(post("/api/admin/recruiters/{id}/approve", recruiterA)
                        .header(HttpHeaders.AUTHORIZATION, adminToken("tenant-a")))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(recruiterA).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    private String seedRecruiter(String tenantId, String emailAddr) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(emailAddr);
        u.setPasswordHash("hash");
        u.setRole(Role.RECRUITER);
        u.setAccountStatus(AccountStatus.PENDING_APPROVAL);
        String userId = userRepository.save(u).getId();

        RecruiterProfile p = new RecruiterProfile();
        p.setTenantId(tenantId);
        p.setUserId(userId);
        p.setCompanyName("Co");
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
