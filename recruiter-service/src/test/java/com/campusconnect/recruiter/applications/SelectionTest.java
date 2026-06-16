package com.campusconnect.recruiter.applications;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationRound;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.InterviewMode;
import com.campusconnect.common.domain.InterviewRound;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.RoundResult;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Final selection (Story 6.5, FR-22): only final-round passers → SELECTED, openings warning, resilient bulk, authz. */
@SpringBootTest
@Testcontainers
class SelectionTest {

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
        mongoTemplate.remove(new Query(), Application.class);
        mongoTemplate.remove(new Query(), ApplicationRound.class);
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        tenantId = seedTenant("vignan");
        recruiterId = seedRecruiter("hr@acme.com", AccountStatus.ACTIVE);
    }

    @Test
    void select_passedFinalRound_marksSelected_audited() throws Exception {
        String drive = seedDrive(recruiterId, 2, 3);
        String app = seedApp(drive, "alice", ApplicationStatus.INTERVIEWING);
        seedRoundRow(app, drive, 2, RoundResult.PASS); // final round passed
        select(drive, app)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.succeeded[0]").value(app))
                .andExpect(jsonPath("$.data.selectedTotal").value(1))
                .andExpect(jsonPath("$.data.warning").doesNotExist()); // 1 < openings 3
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.SELECTED);
        assertThat(mongoTemplate.findAll(AuditLog.class)).extracting(AuditLog::getAction).containsExactly("APPLICANT_SELECTED");
    }

    @Test
    void select_midInterview_failed_notPassedFinalRound() throws Exception {
        String drive = seedDrive(recruiterId, 2, 3);
        String app = seedApp(drive, "alice", ApplicationStatus.INTERVIEWING);
        seedRoundRow(app, drive, 1, RoundResult.PASS); // passed round 1, no round-2 row yet
        select(drive, app)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.failed[0].applicationId").value(app));
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
    }

    @Test
    void select_finalRoundPending_failed() throws Exception {
        String drive = seedDrive(recruiterId, 2, 3);
        String app = seedApp(drive, "alice", ApplicationStatus.INTERVIEWING);
        seedRoundRow(app, drive, 2, RoundResult.PENDING); // in the final round but not yet passed
        select(drive, app).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCount").value(1));
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
    }

    @Test
    void select_bulk_mixed_splitsSucceededAndFailed() throws Exception {
        String drive = seedDrive(recruiterId, 2, 5);
        String passed = seedApp(drive, "alice", ApplicationStatus.INTERVIEWING);
        seedRoundRow(passed, drive, 2, RoundResult.PASS);
        String notPassed = seedApp(drive, "bob", ApplicationStatus.INTERVIEWING);
        seedRoundRow(notPassed, drive, 2, RoundResult.PENDING);
        select(drive, passed, notPassed, "ghost-id")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.succeeded[0]").value(passed));
        assertThat(mongoTemplate.findById(passed, Application.class).getStatus()).isEqualTo(ApplicationStatus.SELECTED);
        assertThat(mongoTemplate.findById(notPassed, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
    }

    @Test
    void select_reachingOpenings_warns_andOverSelectionAllowed() throws Exception {
        String drive = seedDrive(recruiterId, 1, 2); // openings = 2
        String a = passer(drive, "a");
        String b = passer(drive, "b");
        String c = passer(drive, "c");
        select(drive, a, b, c) // select 3 against 2 openings
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(3))
                .andExpect(jsonPath("$.data.selectedTotal").value(3))
                .andExpect(jsonPath("$.data.warning").exists());
        assertThat(mongoTemplate.findById(a, Application.class).getStatus()).isEqualTo(ApplicationStatus.SELECTED);
        assertThat(mongoTemplate.findById(c, Application.class).getStatus()).isEqualTo(ApplicationStatus.SELECTED);
    }

    @Test
    void select_finalRoundFail_failed() throws Exception {
        String drive = seedDrive(recruiterId, 2, 3);
        // already rejected by a final-round FAIL (Story 6.4) — a FAIL row is result != PASS → not selectable
        String app = seedApp(drive, "alice", ApplicationStatus.REJECTED);
        seedRoundRow(app, drive, 2, RoundResult.FAIL);
        select(drive, app).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCount").value(1));
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void select_alreadySelected_failed() throws Exception {
        String drive = seedDrive(recruiterId, 1, 3);
        String app = seedApp(drive, "alice", ApplicationStatus.SELECTED);
        seedRoundRow(app, drive, 1, RoundResult.PASS);
        select(drive, app).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCount").value(1));
    }

    // ── ownership + validation + authz ──

    @Test
    void select_otherRecruitersDrive_is404() throws Exception {
        String otherRecruiter = seedRecruiter("hr2@beta.com", AccountStatus.ACTIVE);
        String otherDrive = seedDrive(otherRecruiter, 1, 3);
        String app = seedApp(otherDrive, "alice", ApplicationStatus.INTERVIEWING);
        seedRoundRow(app, otherDrive, 1, RoundResult.PASS);
        select(otherDrive, app).andExpect(status().isNotFound());
    }

    @Test
    void select_otherTenantsDrive_is404() throws Exception {
        String otherTenant = seedTenant("other");
        Drive d = new Drive();
        d.setTenantId(otherTenant);
        d.setCreatedBy("ghost");
        d.setCompanyName("Acme");
        d.setRole("SDE");
        d.setStatus(DriveStatus.PUBLISHED);
        d.setOpenings(3);
        String foreignDrive = mongoTemplate.save(d).getId();
        select(foreignDrive, "any").andExpect(status().isNotFound());
    }

    @Test
    void select_emptyList_is400() throws Exception {
        String drive = seedDrive(recruiterId, 1, 3);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/select", drive)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void select_blankApplicationId_is400() throws Exception {
        String drive = seedDrive(recruiterId, 1, 3);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/select", drive)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationIds\":[\"  \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void select_noToken_is401() throws Exception {
        String drive = seedDrive(recruiterId, 1, 3);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/select", drive)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationIds\":[\"x\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void select_studentToken_is403() throws Exception {
        String drive = seedDrive(recruiterId, 1, 3);
        String student = seedUser("stud@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/select", drive)
                        .header(HttpHeaders.AUTHORIZATION, token(student, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationIds\":[\"x\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void select_adminToken_is403() throws Exception {
        String drive = seedDrive(recruiterId, 1, 3);
        String admin = seedUser("dean@v.edu", Role.COLLEGE_ADMIN, AccountStatus.ACTIVE);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/select", drive)
                        .header(HttpHeaders.AUTHORIZATION, token(admin, Role.COLLEGE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationIds\":[\"x\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── helpers ──

    /** Seed an INTERVIEWING applicant who passed the (single) final round of {@code drive}. */
    private String passer(String drive, String studentId) {
        String app = seedApp(drive, studentId, ApplicationStatus.INTERVIEWING);
        seedRoundRow(app, drive, 1, RoundResult.PASS);
        return app;
    }

    private org.springframework.test.web.servlet.ResultActions select(String drive, String... applicationIds) throws Exception {
        return mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/select", drive)
                .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("applicationIds", List.of(applicationIds)))));
    }

    private String seedApp(String driveId, String studentId, ApplicationStatus status) {
        Application a = new Application();
        a.setTenantId(tenantId);
        a.setStudentId(studentId);
        a.setDriveId(driveId);
        a.setStatus(status);
        a.setAppliedAt(Instant.parse("2026-06-01T00:00:00Z"));
        a.setResumeSnapshotKey("resumes/" + studentId + "/snap.pdf");
        return mongoTemplate.save(a).getId();
    }

    private void seedRoundRow(String applicationId, String driveId, int roundOrder, RoundResult result) {
        ApplicationRound r = new ApplicationRound();
        r.setTenantId(tenantId);
        r.setApplicationId(applicationId);
        r.setDriveId(driveId);
        r.setRoundOrder(roundOrder);
        r.setResult(result);
        mongoTemplate.save(r);
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

    private String seedUser(String email, Role role, AccountStatus status) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(status);
        return userRepository.save(u).getId();
    }

    private String seedRecruiter(String email, AccountStatus status) {
        String id = seedUser(email, Role.RECRUITER, status);
        RecruiterProfile p = new RecruiterProfile();
        p.setTenantId(tenantId);
        p.setUserId(id);
        p.setCompanyName("Acme Corp");
        mongoTemplate.save(p);
        return id;
    }

    private String seedDrive(String createdBy, int roundCount, int openings) {
        Drive d = new Drive();
        d.setTenantId(tenantId);
        d.setCreatedBy(createdBy);
        d.setCompanyName("Acme Corp");
        d.setRole("SDE-1");
        d.setStatus(DriveStatus.PUBLISHED);
        d.setOpenings(openings);
        List<InterviewRound> rounds = new ArrayList<>();
        for (int i = 1; i <= roundCount; i++) {
            InterviewRound r = new InterviewRound();
            r.setRoundOrder(i);
            r.setName("Round " + i);
            r.setMode(InterviewMode.ONLINE);
            r.setSchedule(Instant.parse("2027-06-01T10:00:00Z"));
            r.setVenueOrLink("https://meet/" + i);
            rounds.add(r);
        }
        d.setRounds(rounds);
        return mongoTemplate.save(d).getId();
    }
}
