package com.campusconnect.student.platform;

import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.repository.TenantRepository;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the tenant-provisioning endpoint through the full stack (security chain +
 * envelope + tenancy + real MongoDB). Exercises the whole 1.2–1.4 foundation via real HTTP.
 */
@SpringBootTest
@Testcontainers
class TenantProvisioningTest {

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
    MongoTemplate mongoTemplate;

    MockMvc mockMvc;

    // Constructed (not autowired) — this Boot 4 context exposes no ObjectMapper bean; findAndRegister
    // picks up the JSR-310 module so LocalDate serializes.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), Tenant.class);
    }

    @Test
    void platformAdmin_createsTenant_201_andPersists() throws Exception {
        mockMvc.perform(post("/api/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("vignan"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.slug").value("vignan"))
                .andExpect(jsonPath("$.data.subdomain").value("vignan"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(tenantRepository.findBySlug("vignan")).isPresent();
    }

    @Test
    void duplicateSlug_isRejectedWith409() throws Exception {
        mockMvc.perform(post("/api/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("dup-college"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("dup-college"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TENANT_SLUG_TAKEN"));
    }

    @Test
    void nonPlatformAdmin_isForbidden403() throws Exception {
        mockMvc.perform(post("/api/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("kits"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void noToken_isUnauthorized401() throws Exception {
        mockMvc.perform(post("/api/platform/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("anon"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidBody_isRejectedWith400AndFields() throws Exception {
        CreateTenantRequest bad = new CreateTenantRequest(
                "", "", null, List.of(), List.of(), null, null);

        mockMvc.perform(post("/api/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields").exists());
    }

    @Test
    void invertedSeasonDates_isRejectedWith400() throws Exception {
        CreateTenantRequest inverted = new CreateTenantRequest(
                "Reverse College", "reverse", null,
                List.of("CSE"), List.of("2026"),
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 6, 1)); // end before start

        mockMvc.perform(post("/api/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(inverted)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void blankBranchElement_isRejectedWith400() throws Exception {
        CreateTenantRequest blankBranch = new CreateTenantRequest(
                "Blank College", "blank", null,
                List.of("CSE", "  "), List.of("2026"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)); // blank branch element

        mockMvc.perform(post("/api/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(blankBranch)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── helpers ──

    private CreateTenantRequest validRequest(String slug) {
        return new CreateTenantRequest(
                slug + " College", slug, null,
                List.of("CSE", "ECE"), List.of("2026"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31));
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
