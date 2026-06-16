package com.campusconnect.admin.placements;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.PlacementRecord;
import com.campusconnect.common.domain.PlacementStatus;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** College-Admin two-step placement confirmation (Story 7.4, FR-25): confirm + queue + audit + tenant isolation + authz. */
@SpringBootTest
@Testcontainers
class PlacementConfirmationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    String tenantId;
    String adminId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), PlacementRecord.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        tenantId = seedTenant("vignan");
        // The JWT filter's status gate (Story 2.5) requires the token's user to exist + be ACTIVE.
        adminId = seedUser(tenantId, "admin@v.edu", Role.COLLEGE_ADMIN);
    }

    @Test
    void confirm_pending_marksOfficiallyPlaced_andAudits() throws Exception {
        String id = seedPlacement(tenantId, "alice", "app-1", PlacementStatus.PENDING_CONFIRMATION);

        mockMvc.perform(post("/api/admin/placements/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, token(Role.COLLEGE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFICIALLY_PLACED"))
                .andExpect(jsonPath("$.data.company").value("Acme Corp"));

        assertThat(mongoTemplate.findById(id, PlacementRecord.class).getStatus())
                .isEqualTo(PlacementStatus.OFFICIALLY_PLACED);

        AuditLog audit = mongoTemplate.findOne(
                new Query(Criteria.where("entityId").is(id).and("action").is("PLACEMENT_CONFIRMED")), AuditLog.class);
        assertThat(audit).isNotNull();
        assertThat(audit.getActor()).isEqualTo(adminId);
        assertThat(audit.getEntityType()).isEqualTo("PlacementRecord");
        assertThat(audit.getOldValue()).isEqualTo("status=PENDING_CONFIRMATION");
        assertThat(audit.getNewValue()).isEqualTo("status=OFFICIALLY_PLACED");
    }

    @Test
    void confirm_alreadyOfficiallyPlaced_is409_unchanged_noSecondAudit() throws Exception {
        String id = seedPlacement(tenantId, "alice", "app-1", PlacementStatus.OFFICIALLY_PLACED);

        mockMvc.perform(post("/api/admin/placements/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, token(Role.COLLEGE_ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(mongoTemplate.findById(id, PlacementRecord.class).getStatus())
                .isEqualTo(PlacementStatus.OFFICIALLY_PLACED);
        assertThat(mongoTemplate.count(
                new Query(Criteria.where("action").is("PLACEMENT_CONFIRMED")), AuditLog.class)).isZero();
    }

    @Test
    void list_defaultsToPendingQueue_andHonorsStatusFilter_tenantScoped() throws Exception {
        seedPlacement(tenantId, "alice", "app-1", PlacementStatus.PENDING_CONFIRMATION);
        seedPlacement(tenantId, "bob", "app-2", PlacementStatus.OFFICIALLY_PLACED);
        // another tenant's PENDING record must NOT appear in this admin's queue (findByStatus is tenant-scoped)
        String otherTenant = seedTenant("other");
        seedPlacement(otherTenant, "carol", "app-foreign", PlacementStatus.PENDING_CONFIRMATION);

        mockMvc.perform(get("/api/admin/placements").header(HttpHeaders.AUTHORIZATION, token(Role.COLLEGE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].applicationId").value("app-1"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_CONFIRMATION"));

        mockMvc.perform(get("/api/admin/placements").param("status", "OFFICIALLY_PLACED")
                        .header(HttpHeaders.AUTHORIZATION, token(Role.COLLEGE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].applicationId").value("app-2"));
    }

    @Test
    void confirm_anotherTenantsPlacement_is404() throws Exception {
        String otherTenant = seedTenant("other");
        String id = seedPlacement(otherTenant, "ghost", "app-x", PlacementStatus.PENDING_CONFIRMATION);

        mockMvc.perform(post("/api/admin/placements/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, token(Role.COLLEGE_ADMIN)))
                .andExpect(status().isNotFound());

        assertThat(mongoTemplate.findById(id, PlacementRecord.class).getStatus())
                .isEqualTo(PlacementStatus.PENDING_CONFIRMATION);
    }

    @Test
    void confirm_missingPlacement_is404() throws Exception {
        mockMvc.perform(post("/api/admin/placements/{id}/confirm", "nonexistent")
                        .header(HttpHeaders.AUTHORIZATION, token(Role.COLLEGE_ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirm_asRecruiter_is403() throws Exception {
        String id = seedPlacement(tenantId, "alice", "app-1", PlacementStatus.PENDING_CONFIRMATION);
        mockMvc.perform(post("/api/admin/placements/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, token(Role.RECRUITER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirm_asStudent_is403() throws Exception {
        String id = seedPlacement(tenantId, "alice", "app-1", PlacementStatus.PENDING_CONFIRMATION);
        mockMvc.perform(post("/api/admin/placements/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, token(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirm_noToken_is401() throws Exception {
        mockMvc.perform(post("/api/admin/placements/{id}/confirm", "any"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    private String token(Role role) {
        return "Bearer " + jwtService.issueAccessToken(adminId, role, tenantId);
    }

    private String seedPlacement(String tid, String studentId, String applicationId, PlacementStatus status) {
        PlacementRecord r = new PlacementRecord();
        r.setTenantId(tid);
        r.setStudentId(studentId);
        r.setApplicationId(applicationId);
        r.setCompany("Acme Corp");
        r.setCtc(12.5);
        r.setRole("SDE-1");
        r.setJoiningDate(Instant.parse("2030-07-01T00:00:00Z"));
        r.setStatus(status);
        return mongoTemplate.save(r).getId();
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
}
