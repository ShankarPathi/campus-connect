package com.campusconnect.student.resume;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Resume;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.StudentProfile;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resume upload/preview (Story 3.2, FR-8): MIME-by-content + size validation, one-active versioning, pre-signed preview, authz — against real MongoDB + MinIO. */
@SpringBootTest
@Testcontainers
class ResumeUploadTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");
    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
        registry.add("app.storage.endpoint", MINIO::getS3URL);
        registry.add("app.storage.access-key", MINIO::getUserName);
        registry.add("app.storage.secret-key", MINIO::getPassword);
        registry.add("app.storage.bucket", () -> "test-resumes");
        registry.add("app.resume.max-size-bytes", () -> "1024"); // 1 KB → tiny oversize fixtures
    }

    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    String tenantId;
    String studentId;

    private static final byte[] TINY_PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF"
            .getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Resume.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        tenantId = seedTenant("vignan");
        studentId = seedUser(tenantId, "s@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
    }

    // ── upload + validation ──

    @Test
    void uploadPdf_storesActiveVersion1() throws Exception {
        mockMvc.perform(multipart("/api/student/resume").file(pdf("resume.pdf", TINY_PDF))
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasResume").value(true))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.originalName").value("resume.pdf"));

        List<Resume> all = mongoTemplate.findAll(Resume.class);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).isActive()).isTrue();
        assertThat(all.get(0).getS3Key()).isNotBlank();
    }

    @Test
    void reupload_makesV2Active_andDeactivatesV1() throws Exception {
        upload(TINY_PDF);
        upload(TINY_PDF);

        List<Resume> all = mongoTemplate.findAll(Resume.class);
        assertThat(all).hasSize(2);
        assertThat(all.stream().filter(Resume::isActive).count()).isEqualTo(1); // exactly one active
        Resume active = all.stream().filter(Resume::isActive).findFirst().orElseThrow();
        assertThat(active.getVersion()).isEqualTo(2);
    }

    @Test
    void nonPdf_namedPdf_is400InvalidType() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        mockMvc.perform(multipart("/api/student/resume").file(pdf("resume.pdf", png))
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESUME_INVALID_TYPE"));
    }

    @Test
    void oversize_is400TooLarge() throws Exception {
        byte[] big = new byte[2048]; // > 1 KB limit
        System.arraycopy(TINY_PDF, 0, big, 0, TINY_PDF.length); // valid PDF header, oversized body
        mockMvc.perform(multipart("/api/student/resume").file(pdf("resume.pdf", big))
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESUME_TOO_LARGE"));
    }

    @Test
    void emptyFile_is400Validation() throws Exception {
        mockMvc.perform(multipart("/api/student/resume").file(pdf("resume.pdf", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── preview ──

    @Test
    void getResume_returnsMetadataAndPresignedPreviewUrl() throws Exception {
        upload(TINY_PDF);
        mockMvc.perform(get("/api/student/resume").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasResume").value(true))
                .andExpect(jsonPath("$.data.previewExpiresInSeconds").value(900))
                .andExpect(jsonPath("$.data.previewUrl").value(org.hamcrest.Matchers.containsString("test-resumes")));
    }

    @Test
    void getResume_whenNone_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/student/resume").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasResume").value(false))
                .andExpect(jsonPath("$.data.previewUrl").doesNotExist());
    }

    // ── season edit-lock (Story 3.4) ──

    @Test
    void upload_whenProfileLocked_is409ProfileLocked() throws Exception {
        seedProfile(true);
        mockMvc.perform(multipart("/api/student/resume").file(pdf("resume.pdf", TINY_PDF))
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_LOCKED"));
        assertThat(mongoTemplate.findAll(Resume.class)).isEmpty(); // nothing stored
    }

    @Test
    void upload_whenProfileUnlocked_stillWorks() throws Exception {
        seedProfile(false); // a profile exists but is not frozen → upload proceeds (regression)
        mockMvc.perform(multipart("/api/student/resume").file(pdf("resume.pdf", TINY_PDF))
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    // ── authz ──

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/student/resume")).andExpect(status().isUnauthorized());
    }

    @Test
    void recruiterToken_is403Forbidden() throws Exception {
        String recruiter = seedUser(tenantId, "hr@v.edu", Role.RECRUITER, AccountStatus.ACTIVE);
        mockMvc.perform(get("/api/student/resume").header(HttpHeaders.AUTHORIZATION, token(recruiter, Role.RECRUITER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void deactivatedStudent_is403AccountInactive() throws Exception {
        String dead = seedUser(tenantId, "dead@v.edu", Role.STUDENT, AccountStatus.DEACTIVATED);
        mockMvc.perform(get("/api/student/resume").header(HttpHeaders.AUTHORIZATION, token(dead, Role.STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    // ── helpers ──

    private void upload(byte[] bytes) throws Exception {
        mockMvc.perform(multipart("/api/student/resume").file(pdf("resume.pdf", bytes))
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk());
    }

    private MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/pdf", bytes);
    }

    /** Seeds a minimal profile for the test student, locked or not (Story 3.4 résumé-lock cases). */
    private void seedProfile(boolean locked) {
        StudentProfile p = new StudentProfile();
        p.setTenantId(tenantId);
        p.setStudentId(studentId);
        p.setLocked(locked);
        mongoTemplate.save(p);
    }

    private String token(String userId, Role role) {
        return "Bearer " + jwtService.issueAccessToken(userId, role, tenantId);
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(List.of("CSE"));
        t.setBatches(List.of("2026"));
        t.setSeason(new Season(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)));
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedUser(String tid, String email, Role role, AccountStatus status) {
        User u = new User();
        u.setTenantId(tid);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(status);
        return userRepository.save(u).getId();
    }
}
