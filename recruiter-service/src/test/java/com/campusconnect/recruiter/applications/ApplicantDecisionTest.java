package com.campusconnect.recruiter.applications;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.RecruiterProfile;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recruiter shortlist/reject (Story 6.2, FR-19): single + bulk, per-item summary, lifecycle guard, audit, ownership 404, authz. */
@SpringBootTest
@Testcontainers
class ApplicantDecisionTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), Application.class);
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        mongoTemplate.remove(new Query(), com.campusconnect.common.domain.Notification.class);
        tenantId = seedTenant("vignan");
        recruiterId = seedRecruiter("hr@acme.com", AccountStatus.ACTIVE);
        driveId = seedDrive(tenantId, recruiterId);
    }

    // ── shortlist ──

    @Test
    void shortlist_single_appliedToShortlisted_audited() throws Exception {
        String appId = seedApplication(tenantId, driveId, "alice", ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(ids(appId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.succeeded[0]").value(appId));
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(mongoTemplate.findAll(AuditLog.class)).extracting(AuditLog::getAction).containsExactly("APPLICANT_SHORTLISTED");
        // Story 8.1: shortlisting notifies the student in-app.
        assertThat(mongoTemplate.findAll(com.campusconnect.common.domain.Notification.class))
                .extracting(com.campusconnect.common.domain.Notification::getUserId,
                        n -> n.getType().name())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("alice", "APPLICATION_SHORTLISTED"));
    }

    @Test
    void shortlist_bulk_mixed_splitsSucceededAndFailed() throws Exception {
        String ok = seedApplication(tenantId, driveId, "alice", ApplicationStatus.APPLIED);
        String withdrawn = seedApplication(tenantId, driveId, "bob", ApplicationStatus.WITHDRAWN);
        String already = seedApplication(tenantId, driveId, "cara", ApplicationStatus.SHORTLISTED);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(ids(ok, withdrawn, already)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.succeeded[0]").value(ok));
        assertThat(mongoTemplate.findById(ok, Application.class).getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(mongoTemplate.findById(withdrawn, Application.class).getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(mongoTemplate.findById(already, Application.class).getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
    }

    @Test
    void shortlist_crossDriveId_isFailedNotFound() throws Exception {
        String otherDrive = seedDrive(tenantId, recruiterId); // also mine, different drive
        String appId = seedApplication(tenantId, otherDrive, "alice", ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(ids(appId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.failed[0].applicationId").value(appId));
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    // ── reject ──

    @Test
    void reject_single_appliedToRejected_audited() throws Exception {
        String appId = seedApplication(tenantId, driveId, "alice", ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/reject", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(ids(appId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1));
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(mongoTemplate.findAll(AuditLog.class)).extracting(AuditLog::getAction).containsExactly("APPLICANT_REJECTED");
    }

    @Test
    void reject_fromShortlisted_isLegal() throws Exception {
        String appId = seedApplication(tenantId, driveId, "alice", ApplicationStatus.SHORTLISTED);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/reject", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(ids(appId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1));
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    // ── ownership 404 ──

    @Test
    void shortlist_otherRecruitersDrive_is404() throws Exception {
        String otherRecruiter = seedRecruiter("hr2@beta.com", AccountStatus.ACTIVE);
        String otherDrive = seedDrive(tenantId, otherRecruiter);
        String appId = seedApplication(tenantId, otherDrive, "alice", ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", otherDrive)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(ids(appId)))
                .andExpect(status().isNotFound());
        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    void shortlist_otherTenantsDrive_is404() throws Exception {
        String otherTenant = seedTenant("other");
        String foreignDrive = seedDrive(otherTenant, "ghost");
        String appId = seedApplication(otherTenant, foreignDrive, "alice", ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", foreignDrive)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content(ids(appId)))
                .andExpect(status().isNotFound());
    }

    // ── validation + authz ──

    @Test
    void shortlist_emptyList_is400() throws Exception {
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shortlist_blankId_is400() throws Exception {
        // a blank id is malformed input → 400 up front, not a misleading per-item "not found"
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationIds\":[\"  \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shortlist_noToken_is401() throws Exception {
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", driveId)
                        .contentType(MediaType.APPLICATION_JSON).content(ids("any")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shortlist_studentToken_is403() throws Exception {
        String student = seedUser("stud@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/shortlist", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(student, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(ids("any")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void reject_adminToken_is403() throws Exception {
        String admin = seedUser("dean@v.edu", Role.COLLEGE_ADMIN, AccountStatus.ACTIVE);
        mockMvc.perform(post("/api/recruiter/drives/{d}/applicants/reject", driveId)
                        .header(HttpHeaders.AUTHORIZATION, token(admin, Role.COLLEGE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(ids("any")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── helpers ──

    private String ids(String... applicationIds) throws Exception {
        return objectMapper.writeValueAsString(Map.of("applicationIds", List.of(applicationIds)));
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
