package com.campusconnect.admin.profiles;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR-1 cross-tenant isolation for student-profile approval (Story 3.3): a COLLEGE_ADMIN of tenant A must
 * NOT approve, reject, edit, or list tenant B's student profile — every reach across tenants is a 404.
 */
@SpringBootTest
@Testcontainers
class ProfileApprovalIsolationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @TestConfiguration
    static class RecordingMailConfig {
        @Bean @Primary EmailService email() {
            return new EmailService() {
                @Override public void sendVerificationEmail(String toEmail, String link) {
                }
                @Override public void sendEmail(String to, String subject, String body) {
                }
            };
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        seedTenant("tenant-a");
        seedTenant("tenant-b");
        seedActiveAdmin("admin-1", "tenant-a");           // the acting admin (tenant A)
        seedProfile("tenant-b", "bob", ProfileApprovalStatus.PENDING_APPROVAL); // a tenant-B profile
    }

    @Test
    void adminOfTenantA_cannotApproveTenantBsProfile() throws Exception {
        mockMvc.perform(post("/api/admin/profiles/{id}/approve", "bob")
                        .header(HttpHeaders.AUTHORIZATION, adminTokenA()))
                .andExpect(status().isNotFound());
        assertThat(profile("bob").getProfileApprovalStatus()).isEqualTo(ProfileApprovalStatus.PENDING_APPROVAL);
        assertThat(mongoTemplate.findAll(AuditLog.class)).isEmpty();
    }

    @Test
    void adminOfTenantA_cannotRejectTenantBsProfile() throws Exception {
        mockMvc.perform(post("/api/admin/profiles/{id}/reject", "bob")
                        .header(HttpHeaders.AUTHORIZATION, adminTokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectProfileRequest("nope"))))
                .andExpect(status().isNotFound());
        assertThat(profile("bob").getRejectionReason()).isNull();
    }

    @Test
    void adminOfTenantA_cannotEditTenantBsProfile() throws Exception {
        mockMvc.perform(patch("/api/admin/profiles/{id}", "bob")
                        .header(HttpHeaders.AUTHORIZATION, adminTokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AdminEditProfileRequest(null, 9.9, null, null))))
                .andExpect(status().isNotFound());
        assertThat(profile("bob").getAcademic().getCgpa()).isEqualTo(8.1); // untouched
    }

    @Test
    void adminList_neverIncludesAnotherTenantsProfiles() throws Exception {
        mockMvc.perform(get("/api/admin/profiles").param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, adminTokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0)); // tenant A has none; B's is invisible
    }

    @Test
    void adminOfTenantA_seasonLock_flipsOnlyTenantAsProfiles() throws Exception {
        seedProfile("tenant-a", "alice", ProfileApprovalStatus.APPROVED); // a tenant-A profile to flip

        mockMvc.perform(post("/api/admin/profiles/lock").header(HttpHeaders.AUTHORIZATION, adminTokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1)); // only tenant A's single profile

        assertThat(profile("alice").isLocked()).isTrue();
        assertThat(profile("bob").isLocked()).isFalse(); // tenant B untouched
        // the lone audit row belongs to tenant A, never tenant B
        List<AuditLog> logs = mongoTemplate.findAll(AuditLog.class);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getTenantId()).isEqualTo("tenant-a");
    }

    // ── helpers ──

    private String adminTokenA() {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.COLLEGE_ADMIN, "tenant-a");
    }

    private StudentProfile profile(String studentId) {
        return mongoTemplate.findAll(StudentProfile.class).stream()
                .filter(p -> studentId.equals(p.getStudentId())).findFirst().orElseThrow();
    }

    private void seedTenant(String tenantId) {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName(tenantId);
        t.setSlug(tenantId);
        t.setBranches(List.of("CSE"));
        t.setBatches(List.of("2026"));
        t.setStatus(TenantStatus.ACTIVE);
        mongoTemplate.save(t);
    }

    private void seedActiveAdmin(String id, String tenantId) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setEmail(id + "@seed.test");
        u.setPasswordHash("hash");
        u.setRole(Role.COLLEGE_ADMIN);
        u.setAccountStatus(AccountStatus.ACTIVE);
        mongoTemplate.save(u);
    }

    private void seedProfile(String tenantId, String studentId, ProfileApprovalStatus status) {
        StudentProfile p = new StudentProfile();
        p.setTenantId(tenantId);
        p.setStudentId(studentId);
        p.setBatch("2026");
        p.getAcademic().setBranch("CSE");
        p.getAcademic().setCgpa(8.1);
        p.getAcademic().setActiveBacklogs(0);
        p.setProfileApprovalStatus(status);
        mongoTemplate.save(p);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
