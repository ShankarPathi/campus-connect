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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Read + reschedule interview rounds (Story 6.3, FR-20): GET counts, reschedule guards (occurred / has-result / past), 404s. */
@SpringBootTest
@Testcontainers
class RoundRescheduleTest {

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
    private static final String FUTURE2 = "2027-09-01T10:00:00Z";

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
        recruiterId = seedRecruiter("hr@acme.com");
        driveId = seedDrive(tenantId, recruiterId);
    }

    @Test
    void get_noRoundsDefined_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rounds.length()").value(0));
    }

    @Test
    void get_returnsSequenceAndAssignedCounts() throws Exception {
        seedApplication(tenantId, driveId, "alice", ApplicationStatus.SHORTLISTED);
        defineOneFutureRound();
        mockMvc.perform(get("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rounds.length()").value(1))
                .andExpect(jsonPath("$.data.rounds[0].name").value("Technical"))
                .andExpect(jsonPath("$.data.rounds[0].assignedCount").value(1));
    }

    @Test
    void reschedule_futureRound_updatesScheduleAndAudits() throws Exception {
        defineOneFutureRound();
        mockMvc.perform(patch("/api/recruiter/drives/{d}/rounds/{o}/reschedule", driveId, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schedule\":\"" + FUTURE2 + "\",\"venueOrLink\":\"https://meet/new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rounds[0].venueOrLink").value("https://meet/new"));
        assertThat(mongoTemplate.findById(driveId, Drive.class).getRounds().get(0).getSchedule())
                .isEqualTo(Instant.parse(FUTURE2));
        assertThat(mongoTemplate.findAll(AuditLog.class)).extracting(AuditLog::getAction).contains("ROUND_RESCHEDULED");
    }

    @Test
    void reschedule_roundThatAlreadyOccurred_is409() throws Exception {
        // seed a drive whose round's schedule is already in the past (can't be created via the @Future API)
        InterviewRound past = new InterviewRound();
        past.setRoundOrder(1);
        past.setName("Technical");
        past.setMode(InterviewMode.ONLINE);
        past.setSchedule(Instant.parse("2020-01-01T00:00:00Z"));
        past.setVenueOrLink("https://meet/x");
        String drive = seedDriveWithRounds(recruiterId, past);
        mockMvc.perform(patch("/api/recruiter/drives/{d}/rounds/{o}/reschedule", drive, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":\"" + FUTURE2 + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void reschedule_roundWithRecordedResult_is409() throws Exception {
        String a1 = seedApplication(tenantId, driveId, "alice", ApplicationStatus.SHORTLISTED);
        defineOneFutureRound(); // assigns a1 to round 1 (PENDING)
        ApplicationRound row = mongoTemplate.find(
                new Query(Criteria.where("applicationId").is(a1)), ApplicationRound.class).get(0);
        row.setResult(RoundResult.PASS); // simulate Story 6.4
        mongoTemplate.save(row);
        mockMvc.perform(patch("/api/recruiter/drives/{d}/rounds/{o}/reschedule", driveId, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":\"" + FUTURE2 + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reschedule_toPastSchedule_is400() throws Exception {
        defineOneFutureRound();
        mockMvc.perform(patch("/api/recruiter/drives/{d}/rounds/{o}/reschedule", driveId, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void reschedule_unknownRound_is404() throws Exception {
        defineOneFutureRound();
        mockMvc.perform(patch("/api/recruiter/drives/{d}/rounds/{o}/reschedule", driveId, 9)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":\"" + FUTURE2 + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reschedule_otherRecruitersDrive_is404() throws Exception {
        String otherRecruiter = seedRecruiter("hr2@beta.com");
        InterviewRound r = new InterviewRound();
        r.setRoundOrder(1);
        r.setName("Technical");
        r.setMode(InterviewMode.ONLINE);
        r.setSchedule(Instant.parse(FUTURE));
        r.setVenueOrLink("x");
        String otherDrive = seedDriveWithRounds(otherRecruiter, r);
        mockMvc.perform(patch("/api/recruiter/drives/{d}/rounds/{o}/reschedule", otherDrive, 1)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":\"" + FUTURE2 + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reschedule_noToken_is401() throws Exception {
        mockMvc.perform(patch("/api/recruiter/drives/{d}/rounds/{o}/reschedule", driveId, 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":\"" + FUTURE2 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    private void defineOneFutureRound() throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", "Technical");
        r.put("mode", "ONLINE");
        r.put("schedule", FUTURE);
        r.put("venueOrLink", "https://meet/x");
        mockMvc.perform(put("/api/recruiter/drives/{d}/rounds", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rounds", List.of(r)))))
                .andExpect(status().isOk());
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

    private String seedDriveWithRounds(String createdBy, InterviewRound... rounds) {
        Drive d = new Drive();
        d.setTenantId(tenantId);
        d.setCreatedBy(createdBy);
        d.setCompanyName("Acme Corp");
        d.setRole("SDE-1");
        d.setStatus(DriveStatus.PUBLISHED);
        d.setRounds(new ArrayList<>(List.of(rounds)));
        return mongoTemplate.save(d).getId();
    }
}
