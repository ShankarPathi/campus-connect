package com.campusconnect.admin.drives;

import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.EligibilityCriteria;
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
 * NFR-1 cross-tenant isolation for drive approval (Story 4.3): a COLLEGE_ADMIN of tenant A must NOT
 * approve, reject, edit, or list tenant B's drive — every reach across tenants is a 404.
 */
@SpringBootTest
@Testcontainers
class DriveApprovalIsolationTest {

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

    String driveB; // a PENDING_APPROVAL drive in tenant B

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        seedTenant("tenant-a");
        seedTenant("tenant-b");
        seedActiveAdmin("admin-1", "tenant-a"); // the acting admin (tenant A)
        driveB = seedDrive("tenant-b", "rec-b");
    }

    @Test
    void adminOfTenantA_cannotApproveTenantBsDrive() throws Exception {
        mockMvc.perform(post("/api/admin/drives/{id}/approve", driveB).header(HttpHeaders.AUTHORIZATION, adminTokenA()))
                .andExpect(status().isNotFound());
        assertThat(mongoTemplate.findById(driveB, Drive.class).getStatus()).isEqualTo(DriveStatus.PENDING_APPROVAL);
        assertThat(mongoTemplate.findAll(AuditLog.class)).isEmpty();
    }

    @Test
    void adminOfTenantA_cannotRejectTenantBsDrive() throws Exception {
        mockMvc.perform(post("/api/admin/drives/{id}/reject", driveB).header(HttpHeaders.AUTHORIZATION, adminTokenA())
                        .contentType(MediaType.APPLICATION_JSON).content(json(new RejectDriveRequest("nope"))))
                .andExpect(status().isNotFound());
        assertThat(mongoTemplate.findById(driveB, Drive.class).getRejectionReason()).isNull();
    }

    @Test
    void adminOfTenantA_cannotEditTenantBsDrive() throws Exception {
        mockMvc.perform(patch("/api/admin/drives/{id}", driveB).header(HttpHeaders.AUTHORIZATION, adminTokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AdminEditDriveCriteriaRequest(null, 9.9, null, null))))
                .andExpect(status().isNotFound());
        assertThat(mongoTemplate.findById(driveB, Drive.class).getEligibility().getMinCgpa()).isEqualTo(7.0); // untouched
    }

    @Test
    void adminList_neverIncludesAnotherTenantsDrives() throws Exception {
        mockMvc.perform(get("/api/admin/drives").param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, adminTokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0)); // tenant A has none; B's is invisible
    }

    // ── helpers ──

    private String adminTokenA() {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.COLLEGE_ADMIN, "tenant-a");
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
        u.setAccountStatus(com.campusconnect.common.domain.AccountStatus.ACTIVE);
        mongoTemplate.save(u);
    }

    private String seedDrive(String tenantId, String createdBy) {
        Drive d = new Drive();
        d.setTenantId(tenantId);
        d.setCreatedBy(createdBy);
        d.setCompanyName("Acme");
        d.setRole("SDE-1");
        EligibilityCriteria e = new EligibilityCriteria();
        e.setMinCgpa(7.0);
        d.setEligibility(e);
        d.setStatus(DriveStatus.PENDING_APPROVAL);
        return mongoTemplate.save(d).getId();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
