package com.campusconnect.student.applications;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.Resume;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apply with résumé snapshot + idempotency (Story 5.4, FR-15). */
@SpringBootTest
@Testcontainers
class ApplyTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
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
        volatile boolean failNext = false;
        @Override public void sendVerificationEmail(String toEmail, String link) {
        }
        @Override public void sendEmail(String to, String subject, String body) {
            if (failNext) {
                throw new RuntimeException("simulated SMTP failure");
            }
            sent.add(new Sent(to, subject, body));
        }
        void clear() {
            sent.clear();
            failNext = false;
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired RecordingEmailService email;

    MockMvc mockMvc;
    String tenantId;
    String studentId;
    String driveId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), Application.class);
        mongoTemplate.remove(new Query(), Resume.class);
        email.clear();
        tenantId = seedTenant("vignan");
        studentId = seedUser(tenantId, "s@v.edu", Role.STUDENT);
        seedApprovedProfile();
        seedActiveResume("resumes/v1.pdf");
        driveId = seedDrive(tenantId, List.of("CSE", "ECE"), DriveStatus.PUBLISHED, futureDeadline());
    }

    @Test
    void apply_eligible_createsAppliedApplication_snapshotsResume_sendsEmail() throws Exception {
        mockMvc.perform(post("/api/student/drives/{id}/apply", driveId).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driveId").value(driveId))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.resumeSnapshotKey").doesNotExist()); // internal — never serialized

        List<Application> apps = mongoTemplate.findAll(Application.class);
        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(apps.get(0).getResumeSnapshotKey()).isEqualTo("resumes/v1.pdf");
        assertThat(apps.get(0).getStudentId()).isEqualTo(studentId);

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).to()).isEqualTo("s@v.edu");
    }

    @Test
    void apply_ineligible_branchMismatch_400NotEligible_noApplication() throws Exception {
        String eceOnly = seedDrive(tenantId, List.of("ECE"), DriveStatus.PUBLISHED, futureDeadline());
        mockMvc.perform(post("/api/student/drives/{id}/apply", eceOnly).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("branch")));

        assertThat(mongoTemplate.findAll(Application.class).stream().filter(a -> eceOnly.equals(a.getDriveId())).toList())
                .isEmpty();
    }

    @Test
    void apply_duplicate_409_andExactlyOneRow() throws Exception {
        mockMvc.perform(post("/api/student/drives/{id}/apply", driveId).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/student/drives/{id}/apply", driveId).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_APPLICATION"));

        assertThat(mongoTemplate.findAll(Application.class)).hasSize(1);
    }

    @Test
    void apply_snapshotFrozen_acrossResumeReplacement() throws Exception {
        mockMvc.perform(post("/api/student/drives/{id}/apply", driveId).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isOk());

        // Replace the active résumé: deactivate v1, add an active v2 with a different key.
        mongoTemplate.findAll(Resume.class).forEach(r -> {
            r.setActive(false);
            mongoTemplate.save(r);
        });
        seedActiveResume("resumes/v2.pdf");

        Application app = mongoTemplate.findAll(Application.class).get(0);
        assertThat(app.getResumeSnapshotKey()).isEqualTo("resumes/v1.pdf"); // frozen, not v2
    }

    @Test
    void apply_noActiveResume_400() throws Exception {
        mongoTemplate.remove(new Query(), Resume.class);
        mockMvc.perform(post("/api/student/drives/{id}/apply", driveId).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(mongoTemplate.findAll(Application.class)).isEmpty();
    }

    @Test
    void apply_crossTenantDrive_404() throws Exception {
        String otherTenant = seedTenant("other");
        String otherDrive = seedDrive(otherTenant, List.of("CSE"), DriveStatus.PUBLISHED, futureDeadline());
        mockMvc.perform(post("/api/student/drives/{id}/apply", otherDrive).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void apply_nonStudent_403() throws Exception {
        mockMvc.perform(post("/api/student/drives/{id}/apply", driveId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken(studentId, Role.RECRUITER, tenantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void apply_emailFailure_applyStillCommits_200() throws Exception {
        // The best-effort contract: a transient SMTP failure must NOT fail the committed apply.
        email.failNext = true;
        mockMvc.perform(post("/api/student/drives/{id}/apply", driveId).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        assertThat(mongoTemplate.findAll(Application.class)).hasSize(1); // row committed despite the email throw
        assertThat(email.sent).isEmpty();                                // the throw was swallowed
    }

    @Test
    void apply_noProfile_400NotEligible_noApplication() throws Exception {
        // A student with no StudentProfile (the common pre-profile state) → null-safe 400, never an NPE.
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mockMvc.perform(post("/api/student/drives/{id}/apply", driveId).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NOT_ELIGIBLE"));

        assertThat(mongoTemplate.findAll(Application.class)).isEmpty();
    }

    @Test
    void apply_sameTenantNonOpenDrive_400NotEligible_not404() throws Exception {
        // A same-tenant DRAFT drive is FOUND (the load doesn't filter by status) → the engine's drive-open
        // rule rejects it as 400 NOT_ELIGIBLE, not 404 (only cross-tenant/missing is 404).
        String draftId = seedDrive(tenantId, List.of("CSE"), DriveStatus.DRAFT, futureDeadline());
        mockMvc.perform(post("/api/student/drives/{id}/apply", draftId).header(HttpHeaders.AUTHORIZATION, studentToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NOT_ELIGIBLE"));

        assertThat(mongoTemplate.findAll(Application.class)).isEmpty();
    }

    @Test
    void uniqueIndex_secondSameTenantStudentDrive_throwsDuplicateKey() {
        // The DB-level idempotency backstop: the unique {tenantId, studentId, driveId} index, independent
        // of the service pre-check.
        mongoTemplate.save(application(tenantId, studentId, driveId));
        assertThatThrownBy(() -> mongoTemplate.save(application(tenantId, studentId, driveId)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ── helpers ──

    private String studentToken() {
        return "Bearer " + jwtService.issueAccessToken(studentId, Role.STUDENT, tenantId);
    }

    private static Instant futureDeadline() {
        return Instant.now().plusSeconds(7 * 86_400);
    }

    private static Application application(String tid, String sid, String did) {
        Application a = new Application();
        a.setTenantId(tid);
        a.setStudentId(sid);
        a.setDriveId(did);
        a.setStatus(ApplicationStatus.APPLIED);
        a.setAppliedAt(Instant.now());
        a.setResumeSnapshotKey("resumes/v1.pdf");
        return a;
    }

    private void seedApprovedProfile() {
        StudentProfile p = new StudentProfile();
        p.setTenantId(tenantId);
        p.setStudentId(studentId);
        p.setRollNumber("21CS001");
        p.setBatch("2026");
        p.getPersonal().setFullName("Asha Rao");
        p.getPersonal().setPhone("9990001234");
        p.getAcademic().setBranch("CSE");
        p.getAcademic().setCgpa(8.1);
        p.getAcademic().setActiveBacklogs(0);
        p.getPlacement().setSkills(List.of("Java"));
        p.setProfileApprovalStatus(ProfileApprovalStatus.APPROVED);
        p.setPlaced(false);
        p.setCompletionPercent(100);
        mongoTemplate.save(p);
    }

    private void seedActiveResume(String s3Key) {
        Resume r = new Resume();
        r.setTenantId(tenantId);
        r.setUserId(studentId);
        r.setS3Key(s3Key);
        r.setOriginalName("resume.pdf");
        r.setMimeType("application/pdf");
        r.setVersion(1);
        r.setActive(true);
        r.setSizeBytes(1024);
        mongoTemplate.save(r);
    }

    private String seedDrive(String tid, List<String> branches, DriveStatus status, Instant deadline) {
        Drive d = new Drive();
        d.setTenantId(tid);
        d.setCreatedBy("rec-1");
        d.setCompanyName("Acme Corp");
        d.setRole("SDE-1");
        d.setPackageLpa(12.0);
        d.setLocation("Bengaluru");
        d.setOpenings(3);
        d.setApplyDeadline(deadline);
        EligibilityCriteria e = new EligibilityCriteria();
        e.setBranches(new ArrayList<>(branches));
        e.setMinCgpa(7.0);
        e.setBacklogPolicy(BacklogPolicy.NO_BACKLOG);
        e.setBatch("2026");
        d.setEligibility(e);
        d.setStatus(status);
        return mongoTemplate.save(d).getId();
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(List.of("CSE", "ECE"));
        t.setBatches(List.of("2026", "2027"));
        t.setSeason(new Season(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)));
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
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
}
