package com.campusconnect.recruiter.offers;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferStatus;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.file.FileStorageService;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Releasing an offer (Story 7.1, FR-23, AC1–9): a recruiter uploads an offer-letter PDF + terms for a
 * {@code SELECTED} applicant, creating the {@code offers} document ({@code PENDING}) and transitioning the
 * application {@code SELECTED → OFFER_RELEASED}. Owner-404 + drive-scope, one-offer-per-application,
 * content+size PDF validation, cross-field deadline rule, audit, and the recruiter verify URL (never the raw
 * key). A {@code @Primary} stub {@link FileStorageService} records {@code put} and returns a canned presigned
 * URL, so no live MinIO is needed (mirrors {@code ApplicantResumeUrlTest}).
 */
@SpringBootTest
@Testcontainers
class OfferReleaseTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    /** Records every {@code put} and echoes the key in the signed URL — so the test asserts storage + URL without MinIO. */
    @TestConfiguration
    static class StubStorageConfig {
        static final List<StoredObject> PUTS = new CopyOnWriteArrayList<>();

        record StoredObject(String key, int size, String contentType) {
        }

        @Bean
        @Primary
        FileStorageService stubFileStorage() {
            return new FileStorageService() {
                @Override
                public void put(String key, byte[] bytes, String contentType) {
                    PUTS.add(new StoredObject(key, bytes.length, contentType));
                }

                @Override
                public String presignedGetUrl(String key, Duration ttl) {
                    return "https://signed.example/" + key + "?ttl=" + ttl.toSeconds();
                }
            };
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    String tenantId;
    String recruiterId;
    String driveId;

    private static final byte[] PDF_BYTES = "%PDF-1.7\nfake offer letter".getBytes(StandardCharsets.UTF_8);
    private static final String VALID_DATA =
            "{\"role\":\"SDE-1\",\"ctc\":12.5,"
                    + "\"joiningDate\":\"2030-07-01T00:00:00Z\","
                    + "\"acceptanceDeadline\":\"2030-06-01T00:00:00Z\"}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        StubStorageConfig.PUTS.clear();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), Application.class);
        mongoTemplate.remove(new Query(), Offer.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
        tenantId = seedTenant("vignan");
        recruiterId = seedRecruiter("hr@acme.com");
        driveId = seedDrive(tenantId, recruiterId);
    }

    // ── happy path ──

    @Test
    void release_selectedApplicant_creates_pending_offer_and_transitions_app() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(appId))
                .andExpect(jsonPath("$.data.studentId").value("alice"))
                .andExpect(jsonPath("$.data.role").value("SDE-1"))
                .andExpect(jsonPath("$.data.ctc").value(12.5))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.acceptanceDeadline").value("2030-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.offerLetterUrl").exists())
                .andExpect(jsonPath("$.data.offerLetterKey").doesNotExist());

        Offer offer = mongoTemplate.findOne(
                new Query(Criteria.where("applicationId").is(appId)), Offer.class);
        assertThat(offer).isNotNull();
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(offer.getStudentId()).isEqualTo("alice");
        assertThat(offer.getRole()).isEqualTo("SDE-1");
        assertThat(offer.getCtc()).isEqualTo(12.5);
        assertThat(offer.getAcceptanceDeadline()).isEqualTo(Instant.parse("2030-06-01T00:00:00Z"));
        assertThat(offer.getJoiningDate()).isEqualTo(Instant.parse("2030-07-01T00:00:00Z"));
        assertThat(offer.getOfferLetterKey())
                .startsWith("offers/" + tenantId + "/" + appId + "/").endsWith(".pdf");

        Application app = mongoTemplate.findById(appId, Application.class);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.OFFER_RELEASED);

        assertThat(StubStorageConfig.PUTS).hasSize(1);
        assertThat(StubStorageConfig.PUTS.get(0).contentType()).isEqualTo("application/pdf");

        AuditLog audit = mongoTemplate.findOne(
                new Query(Criteria.where("entityId").is(appId).and("action").is("OFFER_RELEASED")), AuditLog.class);
        assertThat(audit).isNotNull();
        assertThat(audit.getEntityType()).isEqualTo("Application");
        assertThat(audit.getOldValue()).isEqualTo("status=SELECTED");
        assertThat(audit.getNewValue()).isEqualTo("status=OFFER_RELEASED");
    }

    // ── one offer per application ──

    @Test
    void secondRelease_sameApplication_is409_conflict_appUnchanged() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        assertThat(mongoTemplate.find(new Query(Criteria.where("applicationId").is(appId)), Offer.class)).hasSize(1);
    }

    // ── illegal state ──

    @Test
    void release_nonSelectedApplicant_is409_illegalTransition_nothingWritten() throws Exception {
        String appId = seedApplication(driveId, "bob", ApplicationStatus.INTERVIEWING);

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(mongoTemplate.find(new Query(Criteria.where("applicationId").is(appId)), Offer.class)).isEmpty();
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.INTERVIEWING);
    }

    // ── PDF validation ──

    @Test
    void release_nonPdfFile_is400_nothingWritten() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        MockMultipartFile notPdf = new MockMultipartFile(
                "file", "offer.pdf", "application/pdf", "GIF89a not a pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(notPdf).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(mongoTemplate.find(new Query(Criteria.where("applicationId").is(appId)), Offer.class)).isEmpty();
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.SELECTED);
    }

    @Test
    void release_oversizedPdf_is400() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        byte[] big = new byte[5 * 1024 * 1024 + 16]; // > 5 MB cap, < 10 MB servlet ceiling
        big[0] = '%'; big[1] = 'P'; big[2] = 'D'; big[3] = 'F'; big[4] = '-';
        MockMultipartFile huge = new MockMultipartFile("file", "offer.pdf", "application/pdf", big);

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(huge).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isBadRequest());

        assertThat(mongoTemplate.find(new Query(Criteria.where("applicationId").is(appId)), Offer.class)).isEmpty();
    }

    @Test
    void release_emptyFile_is400_nothingWritten() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        MockMultipartFile empty = new MockMultipartFile("file", "offer.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(empty).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(mongoTemplate.find(new Query(Criteria.where("applicationId").is(appId)), Offer.class)).isEmpty();
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus())
                .isEqualTo(ApplicationStatus.SELECTED);
    }

    // ── metadata validation ──

    @Test
    void release_deadlineAfterJoining_is400() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        String badOrder = "{\"role\":\"SDE-1\",\"ctc\":12.5,"
                + "\"joiningDate\":\"2030-07-01T00:00:00Z\","
                + "\"acceptanceDeadline\":\"2030-08-01T00:00:00Z\"}"; // after joining

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(badOrder))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(mongoTemplate.find(new Query(Criteria.where("applicationId").is(appId)), Offer.class)).isEmpty();
    }

    @Test
    void release_blankRole_is400() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        String blankRole = "{\"role\":\"  \",\"ctc\":12.5,"
                + "\"joiningDate\":\"2030-07-01T00:00:00Z\","
                + "\"acceptanceDeadline\":\"2030-06-01T00:00:00Z\"}";

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(blankRole))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void release_nonPositiveCtc_is400() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        String badCtc = "{\"role\":\"SDE-1\",\"ctc\":0,"
                + "\"joiningDate\":\"2030-07-01T00:00:00Z\","
                + "\"acceptanceDeadline\":\"2030-06-01T00:00:00Z\"}";

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(badCtc))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isBadRequest());
    }

    // ── ownership / tenancy 404 ──

    @Test
    void release_otherRecruitersDrive_is404() throws Exception {
        String otherRecruiter = seedRecruiter("hr2@beta.com");
        String otherDrive = seedDrive(tenantId, otherRecruiter);
        String appId = seedApplication(otherDrive, "carol", ApplicationStatus.SELECTED);

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", otherDrive, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void release_crossTenantDrive_is404() throws Exception {
        String otherTenant = seedTenant("other");
        String foreignDrive = seedDrive(otherTenant, "ghost");
        String appId = seedApplicationIn(otherTenant, foreignDrive, "ghoststudent", ApplicationStatus.SELECTED);

        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", foreignDrive, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void release_wrongDriveApplication_is404() throws Exception {
        String otherDrive = seedDrive(tenantId, recruiterId); // also mine
        String appId = seedApplication(otherDrive, "dave", ApplicationStatus.SELECTED);
        // ask via THIS drive's path for an application that belongs to the other drive → mismatch → 404
        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound());
    }

    // ── authz ──

    @Test
    void release_asStudent_is403() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token("astudent", Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void release_asCollegeAdmin_is403() throws Exception {
        String appId = seedApplication(driveId, "alice", ApplicationStatus.SELECTED);
        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, appId)
                        .file(pdfPart()).file(dataPart(VALID_DATA))
                        .header(HttpHeaders.AUTHORIZATION, token("anadmin", Role.COLLEGE_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void release_noToken_is401() throws Exception {
        mockMvc.perform(multipart("/api/recruiter/drives/{d}/applicants/{a}/offer", driveId, "any")
                        .file(pdfPart()).file(dataPart(VALID_DATA)))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    private MockMultipartFile pdfPart() {
        return new MockMultipartFile("file", "offer.pdf", "application/pdf", PDF_BYTES);
    }

    private MockMultipartFile dataPart(String json) {
        return new MockMultipartFile("data", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private String seedApplication(String drive, String studentId, ApplicationStatus status) {
        return seedApplicationIn(tenantId, drive, studentId, status);
    }

    private String seedApplicationIn(String tenant, String drive, String studentId, ApplicationStatus status) {
        Application a = new Application();
        a.setTenantId(tenant);
        a.setStudentId(studentId);
        a.setDriveId(drive);
        a.setStatus(status);
        a.setAppliedAt(Instant.parse("2026-06-01T00:00:00Z"));
        return mongoTemplate.save(a).getId();
    }

    private String token(String userId, Role role) {
        return "Bearer " + jwtService.issueAccessToken(userId, role, tenantId);
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

    private String seedRecruiter(String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(Role.RECRUITER);
        u.setAccountStatus(AccountStatus.ACTIVE);
        String id = userRepository.save(u).getId();
        RecruiterProfile p = new RecruiterProfile();
        p.setTenantId(tenantId);
        p.setUserId(id);
        p.setCompanyName("Acme Corp");
        mongoTemplate.save(p);
        return id;
    }

    private String seedDrive(String tenant, String createdBy) {
        Drive d = new Drive();
        d.setTenantId(tenant);
        d.setCreatedBy(createdBy);
        d.setCompanyName("Acme Corp");
        d.setRole("SDE-1");
        d.setStatus(DriveStatus.PUBLISHED);
        return mongoTemplate.save(d).getId();
    }
}
