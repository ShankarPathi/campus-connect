package com.campusconnect.admin.drives;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.BacklogPolicy;
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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** College-Admin drive approval/rejection/edit + audit (Story 4.3, FR-11). */
@SpringBootTest
@Testcontainers
class DriveApprovalTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
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

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired RecordingEmailService email;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String TENANT = "tenant-a";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        email.clear();
        seedTenant(TENANT);
        seedActiveUser("admin-1", Role.COLLEGE_ADMIN, TENANT);
        seedActiveUser("rec-1", Role.RECRUITER, TENANT); // the drive owner (for the notify email)
    }

    @Test
    void approve_movesToPublished_audits_andNotifies() throws Exception {
        String id = seedDrive("rec-1", DriveStatus.PENDING_APPROVAL);
        mockMvc.perform(post("/api/admin/drives/{id}/approve", id).header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk());

        assertThat(drive(id).getStatus()).isEqualTo(DriveStatus.PUBLISHED);
        assertThat(audits()).extracting(AuditLog::getAction).containsExactly("DRIVE_APPROVED");
        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).to()).isEqualTo("rec-1@seed.test");
    }

    @Test
    void reject_movesToRejected_storesReason_audits_andNotifiesWithReason() throws Exception {
        String id = seedDrive("rec-1", DriveStatus.PENDING_APPROVAL);
        mockMvc.perform(post("/api/admin/drives/{id}/reject", id).header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectDriveRequest("Package below the college floor"))))
                .andExpect(status().isOk());

        Drive d = drive(id);
        assertThat(d.getStatus()).isEqualTo(DriveStatus.REJECTED_BY_ADMIN);
        assertThat(d.getRejectionReason()).isEqualTo("Package below the college floor");
        assertThat(audits()).extracting(AuditLog::getAction).containsExactly("DRIVE_REJECTED");
        assertThat(email.sent.get(0).body()).contains("Package below the college floor");
    }

    @Test
    void approve_nonPending_is409() throws Exception {
        String id = seedDrive("rec-1", DriveStatus.DRAFT);
        mockMvc.perform(post("/api/admin/drives/{id}/approve", id).header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void editCriteria_raisesMinCgpa_audits_andLeavesStatusUnchanged() throws Exception {
        String id = seedDrive("rec-1", DriveStatus.PENDING_APPROVAL);
        mockMvc.perform(patch("/api/admin/drives/{id}", id).header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AdminEditDriveCriteriaRequest(null, 8.0, null, null))))
                .andExpect(status().isOk());

        Drive d = drive(id);
        assertThat(d.getEligibility().getMinCgpa()).isEqualTo(8.0);
        assertThat(d.getStatus()).isEqualTo(DriveStatus.PENDING_APPROVAL); // unchanged
        List<AuditLog> logs = audits();
        assertThat(logs).extracting(AuditLog::getAction).containsExactly("DRIVE_EDITED");
        assertThat(logs.get(0).getOldValue()).contains("minCgpa=7.0");
        assertThat(logs.get(0).getNewValue()).contains("minCgpa=8.0");
    }

    @Test
    void editCriteria_unknownBranch_is400() throws Exception {
        String id = seedDrive("rec-1", DriveStatus.PENDING_APPROVAL);
        mockMvc.perform(patch("/api/admin/drives/{id}", id).header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AdminEditDriveCriteriaRequest(List.of("MECH"), null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void editCriteria_reorderedSameBranches_isNoOp_noAudit() throws Exception {
        String id = seedDrive("rec-1", DriveStatus.PENDING_APPROVAL); // branches [CSE, ECE]
        mockMvc.perform(patch("/api/admin/drives/{id}", id).header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AdminEditDriveCriteriaRequest(List.of("ECE", "CSE"), null, null, null))))
                .andExpect(status().isOk());
        assertThat(audits()).isEmpty(); // a mere reorder is not a change → no write, no audit row
        assertThat(drive(id).getStatus()).isEqualTo(DriveStatus.PENDING_APPROVAL);
    }

    @Test
    void editCriteria_emptyBranches_is400() throws Exception {
        String id = seedDrive("rec-1", DriveStatus.PENDING_APPROVAL);
        mockMvc.perform(patch("/api/admin/drives/{id}", id).header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AdminEditDriveCriteriaRequest(List.of(), null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_draftStatus_is400() throws Exception {
        mockMvc.perform(get("/api/admin/drives").param("status", "DRAFT").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_returnsOnlyThisTenantsPendingDrives() throws Exception {
        seedDrive("rec-1", DriveStatus.PENDING_APPROVAL);
        seedDrive("rec-1", DriveStatus.PENDING_APPROVAL);
        seedDrive("rec-1", DriveStatus.PUBLISHED); // different status

        mockMvc.perform(get("/api/admin/drives").param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void nonAdmin_isForbidden403() throws Exception {
        String id = seedDrive("rec-1", DriveStatus.PENDING_APPROVAL);
        mockMvc.perform(post("/api/admin/drives/{id}/approve", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken("rec-1", Role.RECRUITER, TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/admin/drives")).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedAdmin_is403AccountInactive() throws Exception {
        seedActiveUser("dead-admin", Role.COLLEGE_ADMIN, TENANT);
        mongoTemplate.findAll(User.class).stream().filter(u -> "dead-admin".equals(u.getId())).forEach(u -> {
            u.setAccountStatus(AccountStatus.DEACTIVATED);
            mongoTemplate.save(u);
        });
        mockMvc.perform(get("/api/admin/drives")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken("dead-admin", Role.COLLEGE_ADMIN, TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    // ── helpers ──

    private String adminToken() {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.COLLEGE_ADMIN, TENANT);
    }

    private Drive drive(String id) {
        return mongoTemplate.findById(id, Drive.class);
    }

    private List<AuditLog> audits() {
        return mongoTemplate.findAll(AuditLog.class);
    }

    private void seedTenant(String tenantId) {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName(tenantId);
        t.setSlug(tenantId);
        t.setBranches(List.of("CSE", "ECE"));
        t.setBatches(List.of("2026", "2027"));
        t.setStatus(TenantStatus.ACTIVE);
        mongoTemplate.save(t);
    }

    private void seedActiveUser(String id, Role role, String tenantId) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setEmail(id + "@seed.test");
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(AccountStatus.ACTIVE);
        mongoTemplate.save(u);
    }

    private String seedDrive(String createdBy, DriveStatus status) {
        Drive d = new Drive();
        d.setTenantId(TENANT);
        d.setCreatedBy(createdBy);
        d.setCompanyName("Acme Corp");
        d.setRole("SDE-1");
        d.setPackageLpa(12.0);
        d.setLocation("Bengaluru");
        d.setOpenings(3);
        d.setApplyDeadline(Instant.parse("2027-01-01T00:00:00Z"));
        EligibilityCriteria e = new EligibilityCriteria();
        e.setBranches(new java.util.ArrayList<>(List.of("CSE", "ECE")));
        e.setMinCgpa(7.0);
        e.setBacklogPolicy(BacklogPolicy.NO_BACKLOG);
        e.setBatch("2026");
        d.setEligibility(e);
        d.setStatus(status);
        return mongoTemplate.save(d).getId();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
