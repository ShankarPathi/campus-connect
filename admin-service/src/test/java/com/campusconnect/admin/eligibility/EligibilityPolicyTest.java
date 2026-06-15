package com.campusconnect.admin.eligibility;

import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.PlacementPolicy;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** College-Admin tenant eligibility policy: set / read-back / audit / validation / isolation (Story 5.2, FR-14). */
@SpringBootTest
@Testcontainers
class EligibilityPolicyTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        seedTenant(TENANT_A);
        seedTenant(TENANT_B);
        seedActiveUser("admin-1", Role.COLLEGE_ADMIN, TENANT_A);
    }

    @Test
    void update_setsPolicy_persists_audits_andReturnsEffective() throws Exception {
        mockMvc.perform(put("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEligibilityPolicyRequest(7.5, true, 15.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minCgpaFloor").value(7.5))
                .andExpect(jsonPath("$.data.placedStudentsMayApply").value(true))
                .andExpect(jsonPath("$.data.reapplyPackageThresholdLpa").value(15.0));

        PlacementPolicy stored = tenant(TENANT_A).getPlacementPolicy();
        assertThat(stored.getMinCgpaFloor()).isEqualTo(7.5);
        assertThat(stored.getPlacedStudentsMayApply()).isTrue();
        assertThat(stored.getReapplyPackageThresholdLpa()).isEqualTo(15.0);

        List<AuditLog> logs = mongoTemplate.findAll(AuditLog.class);
        assertThat(logs).extracting(AuditLog::getAction).containsExactly("POLICY_EDITED");
        assertThat(logs.get(0).getNewValue()).contains("minCgpaFloor=7.5").contains("placedStudentsMayApply=true");
    }

    @Test
    void get_afterUpdate_readsBackEffectivePolicy() throws Exception {
        mockMvc.perform(put("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEligibilityPolicyRequest(6.0, null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minCgpaFloor").value(6.0))
                .andExpect(jsonPath("$.data.placedStudentsMayApply").value(false)) // null → platform default
                .andExpect(jsonPath("$.data.reapplyPackageThresholdLpa").doesNotExist());
    }

    @Test
    void get_freshTenant_returnsPlatformDefaults() throws Exception {
        mockMvc.perform(get("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placedStudentsMayApply").value(false))
                .andExpect(jsonPath("$.data.minCgpaFloor").doesNotExist())
                .andExpect(jsonPath("$.data.reapplyPackageThresholdLpa").doesNotExist());
    }

    @Test
    void update_cgpaFloorAboveTen_is400() throws Exception {
        mockMvc.perform(put("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEligibilityPolicyRequest(11.0, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void update_negativeThreshold_is400() throws Exception {
        mockMvc.perform(put("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEligibilityPolicyRequest(null, true, -5.0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void update_byAdminA_leavesTenantBUntouched() throws Exception {
        mockMvc.perform(put("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEligibilityPolicyRequest(9.0, true, 25.0))))
                .andExpect(status().isOk());

        PlacementPolicy bPolicy = tenant(TENANT_B).getPlacementPolicy();
        assertThat(bPolicy.getMinCgpaFloor()).isNull();
        assertThat(bPolicy.getPlacedStudentsMayApply()).isNull();
        assertThat(bPolicy.getReapplyPackageThresholdLpa()).isNull();
    }

    @Test
    void nonAdmin_isForbidden403() throws Exception {
        seedActiveUser("rec-1", Role.RECRUITER, TENANT_A);
        mockMvc.perform(get("/api/admin/eligibility-policy")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken("rec-1", Role.RECRUITER, TENANT_A)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/admin/eligibility-policy")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_legacyTenantWithEmptyPolicyDocument_returnsPlatformDefaults() throws Exception {
        // Simulate a pre-5.2 tenant: placementPolicy was a Map that defaulted to an empty BSON object {}.
        // The Map → PlacementPolicy retype must deserialize {} into an all-null policy (= inherit defaults).
        mongoTemplate.remove(new Query(), Tenant.class);
        Document legacy = new Document("_id", TENANT_A)
                .append("slug", TENANT_A)
                .append("name", TENANT_A)
                .append("status", "ACTIVE")
                .append("placementPolicy", new Document()); // the old empty-map shape
        mongoTemplate.getCollection("tenants").insertOne(legacy);

        mockMvc.perform(get("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placedStudentsMayApply").value(false))
                .andExpect(jsonPath("$.data.minCgpaFloor").doesNotExist())
                .andExpect(jsonPath("$.data.reapplyPackageThresholdLpa").doesNotExist());
    }

    @Test
    void update_cgpaFloorAtBoundaries_zeroAndTen_accepted() throws Exception {
        mockMvc.perform(put("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEligibilityPolicyRequest(0.0, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minCgpaFloor").value(0.0));

        mockMvc.perform(put("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEligibilityPolicyRequest(10.0, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minCgpaFloor").value(10.0));
    }

    @Test
    void get_missingTenant_is404() throws Exception {
        mongoTemplate.remove(new Query(), Tenant.class); // the admin's tenant doc is gone
        mockMvc.perform(get("/api/admin/eligibility-policy").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ── helpers ──

    private String adminToken() {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.COLLEGE_ADMIN, TENANT_A);
    }

    private Tenant tenant(String id) {
        return mongoTemplate.findById(id, Tenant.class);
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
