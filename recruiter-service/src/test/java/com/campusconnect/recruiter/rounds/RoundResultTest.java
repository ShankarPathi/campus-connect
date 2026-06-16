package com.campusconnect.recruiter.rounds;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Record round results + advance (Story 6.4, FR-21): pass advances, fail/absent rejects, preconditions, bulk split, authz. */
@SpringBootTest
@Testcontainers
class RoundResultTest {

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

    // ── pass / advance ──

    @Test
    void pass_nonFinalRound_advancesToNextRound_appStaysInterviewing_audited() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 2);
        String app = seedApp(drive, "alice", 1, RoundResult.PENDING);
        record(drive, 1, entry(app, "PASS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.succeeded[0]").value(app));
        assertThat(roundRow(app, 1).getResult()).isEqualTo(RoundResult.PASS);
        assertThat(roundRow(app, 2)).isNotNull();
        assertThat(roundRow(app, 2).getResult()).isEqualTo(RoundResult.PENDING);
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        List<AuditLog> audits = mongoTemplate.findAll(AuditLog.class);
        assertThat(audits).singleElement().satisfies(a -> {
            assertThat(a.getAction()).isEqualTo("ROUND_RESULT_RECORDED");
            assertThat(a.getEntityId()).isEqualTo(app);
            assertThat(a.getOldValue()).isEqualTo("round=1 result=PENDING");
            assertThat(a.getNewValue()).isEqualTo("round=1 result=PASS");
        });
    }

    @Test
    void duplicateApplicationId_inOneRequest_recordsOnce_firstWins() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 2);
        String app = seedApp(drive, "alice", 1, RoundResult.PENDING);
        // same id twice; first (PASS) wins, the second (FAIL) is de-duped and silently skipped
        record(drive, 1, entry(app, "PASS"), entry(app, "FAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0));
        assertThat(roundRow(app, 1).getResult()).isEqualTo(RoundResult.PASS); // first wins, not FAIL
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        assertThat(mongoTemplate.findAll(AuditLog.class)).hasSize(1); // recorded once
    }

    @Test
    void pass_finalRound_noNextRow_appStaysInterviewing_notSelected() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 1);
        String app = seedApp(drive, "alice", 1, RoundResult.PENDING);
        record(drive, 1, entry(app, "PASS")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1));
        assertThat(roundRow(app, 1).getResult()).isEqualTo(RoundResult.PASS);
        assertThat(roundRow(app, 2)).isNull(); // no next round
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
    }

    // ── fail / absent → reject ──

    @Test
    void fail_rejectsApplication() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 2);
        String app = seedApp(drive, "alice", 1, RoundResult.PENDING);
        record(drive, 1, entry(app, "FAIL")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1));
        assertThat(roundRow(app, 1).getResult()).isEqualTo(RoundResult.FAIL);
        assertThat(roundRow(app, 2)).isNull();
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void absent_rejectsApplication() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 2);
        String app = seedApp(drive, "alice", 1, RoundResult.PENDING);
        record(drive, 1, entry(app, "ABSENT")).andExpect(status().isOk());
        assertThat(roundRow(app, 1).getResult()).isEqualTo(RoundResult.ABSENT);
        assertThat(mongoTemplate.findById(app, Application.class).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    // ── bulk split ──

    @Test
    void bulk_mixed_splitsSucceededAndFailed() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 2);
        String pass = seedApp(drive, "alice", 1, RoundResult.PENDING);
        String fail = seedApp(drive, "bob", 1, RoundResult.PENDING);
        String already = seedApp(drive, "cara", 1, RoundResult.PASS); // already decided
        record(drive, 1, entry(pass, "PASS"), entry(fail, "FAIL"), entry(already, "PASS"), entry("ghost-id", "PASS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(2));
        assertThat(mongoTemplate.findById(pass, Application.class).getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        assertThat(roundRow(pass, 2)).isNotNull();
        assertThat(mongoTemplate.findById(fail, Application.class).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(roundRow(already, 1).getResult()).isEqualTo(RoundResult.PASS); // unchanged
    }

    @Test
    void pendingResult_isFailedItem() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 2);
        String app = seedApp(drive, "alice", 1, RoundResult.PENDING);
        record(drive, 1, entry(app, "PENDING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.failed[0].applicationId").value(app));
        assertThat(roundRow(app, 1).getResult()).isEqualTo(RoundResult.PENDING); // unchanged
    }

    // ── AC8: the 6.3 orphan-on-shrink deferral is closed by the definition-freeze ──

    @Test
    void shrinkAfterResult_is409() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 2);
        String app = seedApp(drive, "alice", 1, RoundResult.PENDING);
        record(drive, 1, entry(app, "PASS")).andExpect(status().isOk()); // round 1 now PASS → definition frozen
        // shrinking the sequence (2 → 1) would orphan the round-2 row; the 6.3 freeze blocks it
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("name", "Round 1");
        r1.put("mode", "ONLINE");
        r1.put("schedule", "2027-06-01T10:00:00Z");
        r1.put("venueOrLink", "https://meet/1");
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", drive)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rounds", List.of(r1)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    // ── validation + ownership + authz ──

    @Test
    void results_unknownRound_is404() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 1);
        String app = seedApp(drive, "alice", 1, RoundResult.PENDING);
        record(drive, 9, entry(app, "PASS")).andExpect(status().isNotFound());
    }

    @Test
    void results_otherRecruitersDrive_is404() throws Exception {
        String otherRecruiter = seedRecruiter("hr2@beta.com", AccountStatus.ACTIVE);
        String otherDrive = seedDriveWithRounds(otherRecruiter, 1);
        String app = seedApp(otherDrive, "alice", 1, RoundResult.PENDING);
        record(otherDrive, 1, entry(app, "PASS")).andExpect(status().isNotFound());
    }

    @Test
    void results_otherTenantsDrive_is404() throws Exception {
        String otherTenant = seedTenant("other");
        Drive d = new Drive();
        d.setTenantId(otherTenant);
        d.setCreatedBy("ghost");
        d.setCompanyName("Acme");
        d.setRole("SDE");
        d.setStatus(DriveStatus.PUBLISHED);
        InterviewRound r = new InterviewRound();
        r.setRoundOrder(1);
        r.setName("R1");
        r.setMode(InterviewMode.ONLINE);
        r.setSchedule(Instant.parse("2027-06-01T10:00:00Z"));
        r.setVenueOrLink("x");
        d.setRounds(new ArrayList<>(List.of(r)));
        String foreignDrive = mongoTemplate.save(d).getId();
        record(foreignDrive, 1, entry("any", "PASS")).andExpect(status().isNotFound());
    }

    @Test
    void results_emptyList_is400() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 1);
        mockMvc.perform(post("/api/recruiter/drives/{d}/rounds/{o}/results", drive, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"results\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void results_blankApplicationId_is400() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 1);
        mockMvc.perform(post("/api/recruiter/drives/{d}/rounds/{o}/results", drive, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"results\":[{\"applicationId\":\"  \",\"result\":\"PASS\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void results_noToken_is401() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 1);
        mockMvc.perform(post("/api/recruiter/drives/{d}/rounds/{o}/results", drive, 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"results\":[{\"applicationId\":\"x\",\"result\":\"PASS\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void results_studentToken_is403() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 1);
        String student = seedUser("stud@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
        mockMvc.perform(post("/api/recruiter/drives/{d}/rounds/{o}/results", drive, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(student, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"results\":[{\"applicationId\":\"x\",\"result\":\"PASS\"}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void results_adminToken_is403() throws Exception {
        String drive = seedDriveWithRounds(recruiterId, 1);
        String admin = seedUser("dean@v.edu", Role.COLLEGE_ADMIN, AccountStatus.ACTIVE);
        mockMvc.perform(post("/api/recruiter/drives/{d}/rounds/{o}/results", drive, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(admin, Role.COLLEGE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"results\":[{\"applicationId\":\"x\",\"result\":\"PASS\"}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── helpers ──

    private org.springframework.test.web.servlet.ResultActions record(String drive, int roundOrder, Map<String, String>... entries) throws Exception {
        return mockMvc.perform(post("/api/recruiter/drives/{d}/rounds/{o}/results", drive, roundOrder)
                .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("results", List.of(entries)))));
    }

    private Map<String, String> entry(String applicationId, String result) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("applicationId", applicationId);
        m.put("result", result);
        return m;
    }

    private ApplicationRound roundRow(String applicationId, int roundOrder) {
        return mongoTemplate.findOne(new Query(Criteria.where("applicationId").is(applicationId)
                .and("roundOrder").is(roundOrder)), ApplicationRound.class);
    }

    private String seedApp(String driveId, String studentId, int round, RoundResult roundResult) {
        Application a = new Application();
        a.setTenantId(tenantId);
        a.setStudentId(studentId);
        a.setDriveId(driveId);
        a.setStatus(ApplicationStatus.INTERVIEWING);
        a.setAppliedAt(Instant.parse("2026-06-01T00:00:00Z"));
        a.setResumeSnapshotKey("resumes/" + studentId + "/snap.pdf");
        String appId = mongoTemplate.save(a).getId();
        ApplicationRound r = new ApplicationRound();
        r.setTenantId(tenantId);
        r.setApplicationId(appId);
        r.setDriveId(driveId);
        r.setRoundOrder(round);
        r.setResult(roundResult);
        mongoTemplate.save(r);
        return appId;
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

    private String seedDriveWithRounds(String createdBy, int count) {
        Drive d = new Drive();
        d.setTenantId(tenantId);
        d.setCreatedBy(createdBy);
        d.setCompanyName("Acme Corp");
        d.setRole("SDE-1");
        d.setStatus(DriveStatus.PUBLISHED);
        List<InterviewRound> rounds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
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
