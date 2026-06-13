package com.campusconnect.recruiter.drives;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recruiter drive-draft authoring (Story 4.1, FR-10): create/edit/get/list, tenant validation, ownership, authz. */
@SpringBootTest
@Testcontainers
class DriveDraftTest {

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
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    String tenantId;
    String recruiterId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
        tenantId = seedTenant("vignan", List.of("CSE", "ECE"), List.of("2026", "2027"));
        recruiterId = seedRecruiter("hr@acme.com", AccountStatus.ACTIVE, "Acme Corp");
    }

    // ── create / get / list ──

    @Test
    void create_savesDraft_withCompanySnapshot() throws Exception {
        mockMvc.perform(post("/api/recruiter/drives").header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullDrive())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.data.role").value("SDE-1"))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    void getAndList_returnOwnDrive() throws Exception {
        String id = createDrive();
        mockMvc.perform(get("/api/recruiter/drives/{id}", id).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
        mockMvc.perform(get("/api/recruiter/drives").header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void update_inDraft_replacesFields() throws Exception {
        String id = createDrive();
        Map<String, Object> body = fullDrive();
        body.put("role", "Data Analyst");
        body.put("openings", 5);
        mockMvc.perform(put("/api/recruiter/drives/{id}", id).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("Data Analyst"))
                .andExpect(jsonPath("$.data.openings").value(5))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void update_isFullReplace_omittedFieldsAreCleared() throws Exception {
        // Contract: PUT replaces the whole editable set (the 3.1 convention) — a partial body nulls the rest.
        String id = createDrive();
        Map<String, Object> partial = new LinkedHashMap<>();
        partial.put("role", "Only Role"); // everything else omitted
        mockMvc.perform(put("/api/recruiter/drives/{id}", id).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(json(partial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("Only Role"))
                .andExpect(jsonPath("$.data.packageLpa").doesNotExist())   // was 12.0, now cleared
                .andExpect(jsonPath("$.data.openings").doesNotExist())     // was 3, now cleared
                .andExpect(jsonPath("$.data.eligibility.batch").doesNotExist()); // criteria replaced
    }

    @Test
    void create_partialDraft_isAllowed() throws Exception {
        // AC 4a: a draft may be saved incomplete (only a role) — the all-criteria gate is submission (4.2)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", "SDE Intern");
        mockMvc.perform(post("/api/recruiter/drives").header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    // ── validation ──

    @Test
    void create_unknownBranch_is400() throws Exception {
        Map<String, Object> body = fullDrive();
        @SuppressWarnings("unchecked")
        Map<String, Object> e = (Map<String, Object>) body.get("eligibility");
        e.put("branches", List.of("MECH")); // not offered by the college
        expectBadRequest(body);
    }

    @Test
    void create_unknownBatch_is400() throws Exception {
        Map<String, Object> body = fullDrive();
        @SuppressWarnings("unchecked")
        Map<String, Object> e = (Map<String, Object>) body.get("eligibility");
        e.put("batch", "1999");
        expectBadRequest(body);
    }

    @Test
    void create_minCgpaOutOfRange_is400() throws Exception {
        Map<String, Object> body = fullDrive();
        @SuppressWarnings("unchecked")
        Map<String, Object> e = (Map<String, Object>) body.get("eligibility");
        e.put("minCgpa", 11.0);
        expectBadRequest(body);
    }

    @Test
    void create_zeroOpenings_is400() throws Exception {
        Map<String, Object> body = fullDrive();
        body.put("openings", 0);
        expectBadRequest(body);
    }

    // ── authz ──

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentToken_is403Forbidden() throws Exception {
        String student = seedUser("stud@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
        mockMvc.perform(get("/api/recruiter/drives").header(HttpHeaders.AUTHORIZATION, token(student, Role.STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void deactivatedRecruiter_is403AccountInactive() throws Exception {
        String dead = seedRecruiter("dead@acme.com", AccountStatus.DEACTIVATED, "Acme Corp");
        mockMvc.perform(get("/api/recruiter/drives").header(HttpHeaders.AUTHORIZATION, token(dead, Role.RECRUITER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    // ── helpers ──

    private String createDrive() throws Exception {
        String res = mockMvc.perform(post("/api/recruiter/drives").header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullDrive())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).at("/data/id").asText();
    }

    private void expectBadRequest(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/recruiter/drives").header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private Map<String, Object> fullDrive() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", "SDE-1");
        body.put("packageLpa", 12.0);
        body.put("location", "Bengaluru");
        body.put("openings", 3);
        body.put("applyDeadline", "2027-01-01T00:00:00Z");
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("branches", List.of("CSE", "ECE"));
        e.put("minCgpa", 7.0);
        e.put("backlogPolicy", "NO_BACKLOG");
        e.put("batch", "2026");
        body.put("eligibility", e);
        return body;
    }

    private String token(String userId, Role role) {
        return "Bearer " + jwtService.issueAccessToken(userId, role, tenantId);
    }

    private String seedTenant(String slug, List<String> branches, List<String> batches) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(branches);
        t.setBatches(batches);
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedUser(String email, Role role, AccountStatus status) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(status);
        return userRepository.save(u).getId();
    }

    /** Seeds an ACTIVE/other recruiter User + their RecruiterProfile (for the company-name snapshot). */
    private String seedRecruiter(String email, AccountStatus status, String companyName) {
        String id = seedUser(email, Role.RECRUITER, status);
        RecruiterProfile p = new RecruiterProfile();
        p.setTenantId(tenantId);
        p.setUserId(id);
        p.setCompanyName(companyName);
        mongoTemplate.save(p);
        return id;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
