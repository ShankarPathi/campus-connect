package com.campusconnect.student.platform;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ISOLATION GATE (NFR-1 / FR-3) — the mandated cross-tenant isolation test (architecture §4).
 *
 * <p><b>Every new tenant-scoped list/detail endpoint MUST add an "A-cannot-read-B" case here.</b>
 * A failure in this class fails the build. Together with {@code common-lib}'s
 * {@code TenantAwareRepositoryTest} (which proves the {@code TenantAwareRepository} auto-filter at the
 * mechanism level), it guarantees no college can ever read another's data.
 *
 * <p>Covered today (the only tenant-scoped surface that exists): the {@code users} collection created
 * by the bootstrap endpoint. TODO as their endpoints land: student profiles (Epic 3), drives (Epic 4),
 * applications (Epic 5), interview rounds (Epic 6), offers/placements (Epic 7), reports (Epic 8).
 */
@SpringBootTest
@Testcontainers
class CrossTenantIsolationTest {

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
    MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
    }

    @Test
    void usersAreIsolatedPerTenant_andEmailIsUniquePerTenantNotGlobally() throws Exception {
        String tenantA = createTenant("alpha");
        String tenantB = createTenant("beta");

        // same email bootstrapped into BOTH tenants — allowed (per-tenant, not global)
        bootstrapAdmin(tenantA, "shared@tpo.edu");
        bootstrapAdmin(tenantB, "shared@tpo.edu");
        // an A-only user
        bootstrapAdmin(tenantA, "aonly@tpo.edu");

        // each tenant sees its own "shared" user, correctly scoped, and they are distinct records
        User aShared = userRepository.findByTenantIdAndEmail(tenantA, "shared@tpo.edu").orElseThrow();
        User bShared = userRepository.findByTenantIdAndEmail(tenantB, "shared@tpo.edu").orElseThrow();
        assertThat(aShared.getTenantId()).isEqualTo(tenantA);
        assertThat(bShared.getTenantId()).isEqualTo(tenantB);
        assertThat(aShared.getId()).isNotEqualTo(bShared.getId());

        // ISOLATION (the negative assertion): Tenant B's scope NEVER yields Tenant A's A-only user
        assertThat(userRepository.findByTenantIdAndEmail(tenantB, "aonly@tpo.edu")).isEmpty();
        assertThat(userRepository.existsByTenantIdAndEmail(tenantB, "aonly@tpo.edu")).isFalse();
        // ...while Tenant A's own scope does
        assertThat(userRepository.findByTenantIdAndEmail(tenantA, "aonly@tpo.edu")).isPresent();
        assertThat(userRepository.existsByTenantIdAndEmail(tenantA, "aonly@tpo.edu")).isTrue();

        // The data is genuinely partitioned (a tenant-UNAWARE full scan shows both tenants' rows),
        // and the shared email exists exactly once per tenant — proving the {tenantId,email} index is
        // per-tenant, NOT a global {email} unique index (which would have rejected the second bootstrap).
        List<User> all = mongoTemplate.findAll(User.class);
        assertThat(all).hasSize(3);
        assertThat(all).extracting(User::getTenantId).contains(tenantA, tenantB);
        assertThat(all.stream().filter(u -> "shared@tpo.edu".equals(u.getEmail())).map(User::getTenantId))
                .containsExactlyInAnyOrder(tenantA, tenantB);

        // NOTE: the auto-filter-from-context isolation mechanism (TenantAwareRepository) is proven by
        // common-lib's TenantAwareRepositoryTest, which runs in the same CI suite. User uses explicit-
        // tenant queries by design (Story 1.6), so it has no auto-filter path to exercise here.
    }

    @Test
    void duplicateEmailWithinOneTenant_isRejected() throws Exception {
        String tenantA = createTenant("gamma");
        bootstrapAdmin(tenantA, "dup@tpo.edu");

        // second bootstrap of the same email into the SAME tenant → 409 (per-tenant uniqueness)
        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantA)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateCollegeAdminRequest("dup@tpo.edu", "s3cret-pw"))))
                .andExpect(status().isConflict());
    }

    // ── helpers ──

    private void bootstrapAdmin(String tenantId, String email) throws Exception {
        mockMvc.perform(post("/api/platform/tenants/{tenantId}/admins", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateCollegeAdminRequest(email, "s3cret-pw"))))
                .andExpect(status().isCreated());
    }

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

    private String adminToken() {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.PLATFORM_ADMIN, null);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
