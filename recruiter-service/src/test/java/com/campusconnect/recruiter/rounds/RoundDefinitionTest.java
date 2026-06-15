package com.campusconnect.recruiter.rounds;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationRound;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
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
import org.springframework.data.mongodb.core.query.Criteria;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Define interview rounds (Story 6.3, FR-20): sequence + round-1 assignment, transition, idempotency, validation, guards, authz. */
@SpringBootTest
@Testcontainers
class RoundDefinitionTest {

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
    String driveId;

    private static final String FUTURE = "2027-06-01T10:00:00Z";

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
        driveId = seedDrive(tenantId, recruiterId);
    }

    @Test
    void define_assignsShortlistedToRoundOne_transitionsInterviewing_audited() throws Exception {
        String a1 = seedApplication(tenantId, driveId, "alice", ApplicationStatus.SHORTLISTED);
        String a2 = seedApplication(tenantId, driveId, "bob", ApplicationStatus.SHORTLISTED);
        String applied = seedApplication(tenantId, driveId, "cara", ApplicationStatus.APPLIED);

        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(round("Technical", "ONLINE", FUTURE, "https://meet/x"),
                                round("HR", "OFFLINE", FUTURE, "Room 5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rounds.length()").value(2))
                .andExpect(jsonPath("$.data.rounds[0].roundOrder").value(1))
                .andExpect(jsonPath("$.data.rounds[1].roundOrder").value(2))
                .andExpect(jsonPath("$.data.rounds[0].assignedCount").value(2));

        assertThat(mongoTemplate.findById(a1, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        assertThat(mongoTemplate.findById(a2, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        assertThat(mongoTemplate.findById(applied, Application.class).getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(roundRows(a1)).hasSize(1).allMatch(r -> r.getRoundOrder() == 1 && r.getResult() == RoundResult.PENDING);
        assertThat(roundRows(applied)).isEmpty();
        assertThat(mongoTemplate.findAll(AuditLog.class)).extracting(AuditLog::getAction).containsExactly("INTERVIEW_ROUNDS_DEFINED");
    }

    @Test
    void define_idempotentRePut_noDuplicateRows_picksUpNewlyShortlisted() throws Exception {
        String a1 = seedApplication(tenantId, driveId, "alice", ApplicationStatus.SHORTLISTED);
        define(round("Technical", "ONLINE", FUTURE, "https://meet/x"));
        // a1 is now INTERVIEWING with one round-1 row; shortlist another student, then re-PUT
        String a2 = seedApplication(tenantId, driveId, "bob", ApplicationStatus.SHORTLISTED);
        define(round("Technical", "ONLINE", FUTURE, "https://meet/x"));

        assertThat(mongoTemplate.findById(a1, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        assertThat(mongoTemplate.findById(a2, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        assertThat(roundRows(a1)).hasSize(1); // no duplicate
        assertThat(roundRows(a2)).hasSize(1);
        assertThat(mongoTemplate.findAll(ApplicationRound.class)).hasSize(2);
    }

    @Test
    void define_onlyShortlisted_assigned_othersUntouched() throws Exception {
        seedApplication(tenantId, driveId, "ap", ApplicationStatus.APPLIED);
        seedApplication(tenantId, driveId, "rj", ApplicationStatus.REJECTED);
        seedApplication(tenantId, driveId, "wd", ApplicationStatus.WITHDRAWN);
        define(round("Technical", "ONLINE", FUTURE, "https://meet/x"));
        assertThat(mongoTemplate.findAll(ApplicationRound.class)).isEmpty();
        assertThat(mongoTemplate.findAll(Application.class)).extracting(Application::getStatus)
                .containsExactlyInAnyOrder(ApplicationStatus.APPLIED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);
    }

    // ── validation: nothing written ──

    @Test
    void define_emptyRounds_is400() throws Exception {
        expectBadRequest("{\"rounds\":[]}");
    }

    @Test
    void define_pastSchedule_is400() throws Exception {
        expectBadRequest(body(round("Technical", "ONLINE", "2020-01-01T00:00:00Z", "https://meet/x")));
    }

    @Test
    void define_blankName_is400() throws Exception {
        expectBadRequest(body(round("  ", "ONLINE", FUTURE, "https://meet/x")));
    }

    @Test
    void define_missingMode_is400() throws Exception {
        Map<String, Object> r = round("Technical", "ONLINE", FUTURE, "https://meet/x");
        r.remove("mode");
        expectBadRequest(body(r));
    }

    @Test
    void define_validationFailure_writesNothing() throws Exception {
        seedApplication(tenantId, driveId, "alice", ApplicationStatus.SHORTLISTED);
        expectBadRequest("{\"rounds\":[]}");
        assertThat(mongoTemplate.findById(driveId, Drive.class).getRounds()).isEmpty();
        assertThat(mongoTemplate.findAll(ApplicationRound.class)).isEmpty();
    }

    // ── restructure guard ──

    @Test
    void restructure_afterResultRecorded_is409_butSameStructureOk() throws Exception {
        String a1 = seedApplication(tenantId, driveId, "alice", ApplicationStatus.SHORTLISTED);
        define(round("Technical", "ONLINE", FUTURE, "https://meet/x"));
        // simulate Story 6.4 recording a PASS on the round-1 row
        ApplicationRound row = roundRows(a1).get(0);
        row.setResult(RoundResult.PASS);
        mongoTemplate.save(row);

        // changing the structure (1 -> 2 rounds) is now blocked
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(round("Technical", "ONLINE", FUTURE, "https://meet/x"),
                                round("HR", "OFFLINE", FUTURE, "Room 5"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        // an identical-definition re-PUT is still fine
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(round("Technical", "ONLINE", FUTURE, "https://meet/x"))))
                .andExpect(status().isOk());
    }

    @Test
    void changedScheduleAfterResult_viaPut_is409_noRescheduleGuardBypass() throws Exception {
        // a started round's schedule must not be changeable via PUT (bypassing the reschedule guard)
        String a1 = seedApplication(tenantId, driveId, "alice", ApplicationStatus.SHORTLISTED);
        define(round("Technical", "ONLINE", FUTURE, "https://meet/x"));
        ApplicationRound row = roundRows(a1).get(0);
        row.setResult(RoundResult.PASS);
        mongoTemplate.save(row);

        // same name/mode/order, only the schedule differs → still a definition change → 409
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(round("Technical", "ONLINE", "2027-12-01T10:00:00Z", "https://meet/x"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        assertThat(mongoTemplate.findById(driveId, Drive.class).getRounds().get(0).getSchedule())
                .isEqualTo(Instant.parse(FUTURE)); // unchanged
    }

    // ── ownership + authz ──

    @Test
    void define_otherRecruitersDrive_is404() throws Exception {
        String otherRecruiter = seedRecruiter("hr2@beta.com", AccountStatus.ACTIVE);
        String otherDrive = seedDrive(tenantId, otherRecruiter);
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", otherDrive)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(body(round("T", "ONLINE", FUTURE, "x"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void define_otherTenantsDrive_is404() throws Exception {
        String otherTenant = seedTenant("other");
        String foreignDrive = seedDrive(otherTenant, "ghost");
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", foreignDrive)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(body(round("T", "ONLINE", FUTURE, "x"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void define_noToken_is401() throws Exception {
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(round("T", "ONLINE", FUTURE, "x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void define_studentToken_is403() throws Exception {
        String student = seedUser("stud@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(student, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(body(round("T", "ONLINE", FUTURE, "x"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void define_adminToken_is403() throws Exception {
        String admin = seedUser("dean@v.edu", Role.COLLEGE_ADMIN, AccountStatus.ACTIVE);
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(admin, Role.COLLEGE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body(round("T", "ONLINE", FUTURE, "x"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── helpers ──

    private void define(Map<String, Object>... rounds) throws Exception {
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(body(rounds)))
                .andExpect(status().isOk());
    }

    private void expectBadRequest(String json) throws Exception {
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private List<ApplicationRound> roundRows(String applicationId) {
        return mongoTemplate.find(new Query(Criteria.where("applicationId").is(applicationId)), ApplicationRound.class);
    }

    private Map<String, Object> round(String name, String mode, String schedule, String venue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("mode", mode);
        m.put("schedule", schedule);
        m.put("venueOrLink", venue);
        return m;
    }

    @SafeVarargs
    private String body(Map<String, Object>... rounds) throws Exception {
        return objectMapper.writeValueAsString(Map.of("rounds", List.of(rounds)));
    }

    private String seedApplication(String tenant, String drive, String studentId, ApplicationStatus status) {
        Application a = new Application();
        a.setTenantId(tenant);
        a.setStudentId(studentId);
        a.setDriveId(drive);
        a.setStatus(status);
        a.setAppliedAt(Instant.parse("2026-06-01T00:00:00Z"));
        a.setResumeSnapshotKey("resumes/" + tenant + "/" + studentId + "/snap.pdf");
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
