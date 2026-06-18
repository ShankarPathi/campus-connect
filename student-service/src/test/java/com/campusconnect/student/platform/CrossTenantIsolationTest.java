package com.campusconnect.student.platform;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Resume;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.ResumeRepository;
import com.campusconnect.common.repository.StudentProfileRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.tenancy.TenantContext;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
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
    StudentProfileRepository studentProfileRepository;
    @Autowired
    ResumeRepository resumeRepository;
    @Autowired
    MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mongoTemplate.remove(new Query(), Resume.class);
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

    @Test
    void studentInTenantB_cannotSeeTenantAsProfile_throughTheEndpoint() throws Exception {
        String tenantA = createTenant("alpha");
        String tenantB = createTenant("beta");
        String studentA = seedActiveStudent(tenantA, "a@alpha.edu");
        String studentB = seedActiveStudent(tenantB, "b@beta.edu");

        // Student A builds a full profile through the real authenticated endpoint
        mockMvc.perform(put("/api/student/profile")
                        .header(HttpHeaders.AUTHORIZATION, studentToken(studentA, tenantA))
                        .contentType(MediaType.APPLICATION_JSON).content(fullProfileJson("CSE", "2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completionPercent").value(100));

        // ISOLATION (the negative assertion): an ACTIVE student in tenant B, hitting the SAME endpoint,
        // only ever gets their own empty draft — never tenant A's data.
        mockMvc.perform(get("/api/student/profile")
                        .header(HttpHeaders.AUTHORIZATION, studentToken(studentB, tenantB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileApprovalStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.completionPercent").value(0))
                .andExpect(jsonPath("$.data.rollNumber").doesNotExist());

        // Mechanism cross-check: A's profile is visible only under tenant A's scope, never tenant B's.
        runInTenant(tenantB, () -> assertThat(studentProfileRepository.findByStudentId(studentA)).isEmpty());
        runInTenant(tenantA, () -> assertThat(studentProfileRepository.findByStudentId(studentA)).isPresent());
        // genuinely partitioned: exactly one profile exists, and it belongs to tenant A
        assertThat(mongoTemplate.findAll(StudentProfile.class)).hasSize(1);
    }

    @Test
    void resumesAreIsolatedPerTenant() {
        String tenantA = createTenant("ralpha");
        String tenantB = createTenant("rbeta");

        // The SAME userId has a resume in BOTH tenants, so ONLY the tenantId filter can distinguish the
        // rows — this isolates the tenant-scoping mechanism (not the userId, which exists in both).
        // (The resume HTTP/ownership path is covered end-to-end by ResumeUploadTest; the cross-tenant
        // vector for resumes is the tenant-aware repository, which this proves.)
        String sharedUserId = "shared-stud";
        saveResumeInTenant(tenantA, sharedUserId, "a.pdf");
        saveResumeInTenant(tenantB, sharedUserId, "b.pdf");

        // ISOLATION: each tenant's scope resolves only ITS OWN resume for the shared userId, never the other's.
        runInTenant(tenantA, () -> assertThat(resumeRepository.findActiveByUserId(sharedUserId))
                .get().extracting(Resume::getOriginalName).isEqualTo("a.pdf"));
        runInTenant(tenantB, () -> assertThat(resumeRepository.findActiveByUserId(sharedUserId))
                .get().extracting(Resume::getOriginalName).isEqualTo("b.pdf"));
        // genuinely partitioned: a tenant-UNAWARE scan shows both rows
        assertThat(mongoTemplate.findAll(Resume.class)).hasSize(2);
    }

    private void saveResumeInTenant(String tenantId, String userId, String originalName) {
        runInTenant(tenantId, () -> {
            Resume r = new Resume();
            r.setUserId(userId);
            r.setS3Key("resumes/" + tenantId + "/" + userId + "/1-x.pdf");
            r.setOriginalName(originalName);
            r.setMimeType("application/pdf");
            r.setVersion(1);
            r.setActive(true);
            r.setSizeBytes(100);
            resumeRepository.save(r); // tenant-aware save stamps tenantId from context
        });
    }

    // ── helpers ──

    private String seedActiveStudent(String tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(Role.STUDENT);
        u.setAccountStatus(AccountStatus.ACTIVE); // ACTIVE so the Story 2.5 status gate admits the request
        return userRepository.save(u).getId();
    }

    private String studentToken(String userId, String tenantId) {
        return "Bearer " + jwtService.issueAccessToken(userId, Role.STUDENT, tenantId);
    }

    private String fullProfileJson(String branch, String batch) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "personal", java.util.Map.of("fullName", "Asha Rao", "phone", "9990001234"),
                "academic", java.util.Map.of("branch", branch, "cgpa", 8.1, "activeBacklogs", 0),
                "placement", java.util.Map.of("skills", List.of("Java")),
                "rollNumber", "21CS001",
                "batch", batch));
    }

    /** Runs an action with the given tenant bound to the context (cleared in a finally). */
    private void runInTenant(String tenantId, Runnable action) {
        try {
            TenantContext.set(tenantId, "tester", Role.STUDENT.name());
            action.run();
        } finally {
            TenantContext.clear();
        }
    }

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
