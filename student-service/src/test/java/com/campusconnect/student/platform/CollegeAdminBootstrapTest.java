package com.campusconnect.student.platform;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Season;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of College Admin bootstrap (FR-2): security matrix, per-tenant email uniqueness,
 * 404-before-409, and that the password is hashed and never returned.
 */
@SpringBootTest
@Testcontainers
class CollegeAdminBootstrapTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    JwtService jwtService;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        // Story 2.5: the per-request status gate loads the token's user. The PLATFORM_ADMIN token is
        // skipped, but the STUDENT token used by nonPlatformAdmin_isForbidden403 needs a real ACTIVE row
        // so the request reaches @PreAuthorize and is rejected by ROLE, not masked as ACCOUNT_INACTIVE.
        seedActiveUser("student-1", Role.STUDENT, "tenant-x");
    }

    private void seedActiveUser(String id, Role role, String tenantId) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setEmail(id + "@seed.test");
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(u);
    }

    @Test
    void platformAdmin_bootstrapsActiveHashedCollegeAdmin() throws Exception {
        String tenantId = createTenant("vignan");

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("tpo@vignan.edu", "s3cret-pw"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.tenantId").value(tenantId))
                .andExpect(jsonPath("$.data.email").value("tpo@vignan.edu"))
                .andExpect(jsonPath("$.data.role").value("COLLEGE_ADMIN"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                // the password / hash must never be returned
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User saved = userRepository.findByTenantIdAndEmail(tenantId, "tpo@vignan.edu").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.COLLEGE_ADMIN);
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getPasswordHash()).isNotEqualTo("s3cret-pw");           // stored as hash
        assertThat(passwordEncoder.matches("s3cret-pw", saved.getPasswordHash())).isTrue(); // verifies
    }

    @Test
    void sameEmailInDifferentTenant_isAllowed() throws Exception {
        String tenantA = createTenant("alpha");
        String tenantB = createTenant("beta");

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantA)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("tpo@shared.edu", "s3cret-pw"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("tpo@shared.edu", "s3cret-pw"))))
                .andExpect(status().isCreated()); // per-tenant uniqueness, not global
    }

    @Test
    void mixedCaseEmailSameTenant_isRejectedWith409() throws Exception {
        String tenantId = createTenant("normcollege");

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("tpo@norm.edu", "s3cret-pw"))))
                .andExpect(status().isCreated());

        // same logical email, different casing → must be treated as a duplicate (case-insensitive)
        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("TPO@Norm.edu", "s3cret-pw"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void duplicateEmailSameTenant_isRejectedWith409() throws Exception {
        String tenantId = createTenant("dupcollege");

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("dup@dupcollege.edu", "s3cret-pw"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("dup@dupcollege.edu", "s3cret-pw"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void unknownTenant_isRejectedWith404() throws Exception {
        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", "no-such-tenant")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("tpo@ghost.edu", "s3cret-pw"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void nonPlatformAdmin_isForbidden403() throws Exception {
        String tenantId = createTenant("gamma");

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("tpo@gamma.edu", "s3cret-pw"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void noToken_isUnauthorized401() throws Exception {
        String tenantId = createTenant("delta");

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("tpo@delta.edu", "s3cret-pw"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidBody_isRejectedWith400() throws Exception {
        String tenantId = createTenant("epsilon");

        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateCollegeAdminRequest("not-an-email", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields").exists());
    }

    // ── helpers ──

    private String createTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug + " College");
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(List.of("CSE"));
        t.setBatches(List.of("2026"));
        t.setSeason(new Season(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)));
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private CreateCollegeAdminRequest request(String email, String password) {
        return new CreateCollegeAdminRequest(email, password);
    }

    private String adminToken() {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.PLATFORM_ADMIN, null);
    }

    private String studentToken() {
        return "Bearer " + jwtService.issueAccessToken("student-1", Role.STUDENT, "tenant-x");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
