package com.campusconnect.admin.profiles;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** College-Admin student-profile approval/rejection/edit + audit (Story 3.3, FR-9). */
@SpringBootTest
@Testcontainers
class ProfileApprovalTest {

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
        @Override public void sendVerificationEmail(String toEmail, String link) {
        }
        @Override public void sendEmail(String to, String subject, String body) {
            sent.add(new Sent(to, subject, body));
        }
        void clear() {
            sent.clear();
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired RecordingEmailService email;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String TENANT = "tenant-a";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        email.clear();
        seedTenant(TENANT);
        seedActiveUser("admin-1", Role.COLLEGE_ADMIN, TENANT);
    }

    @Test
    void approve_movesToApproved_audits_andNotifies() throws Exception {
        seedStudent("stud-1", "s1@v.edu");
        seedProfile("stud-1", ProfileApprovalStatus.PENDING_APPROVAL, "CSE", 8.1);

        mockMvc.perform(post("/api/admin/profiles/{id}/approve", "stud-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk());

        assertThat(profile("stud-1").getProfileApprovalStatus()).isEqualTo(ProfileApprovalStatus.APPROVED);
        assertThat(audits()).extracting(AuditLog::getAction).containsExactly("PROFILE_APPROVED");
        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).to()).isEqualTo("s1@v.edu");
    }

    @Test
    void reject_movesToRejected_storesReason_audits_andNotifiesWithReason() throws Exception {
        seedStudent("stud-1", "s1@v.edu");
        seedProfile("stud-1", ProfileApprovalStatus.PENDING_APPROVAL, "CSE", 8.1);

        mockMvc.perform(post("/api/admin/profiles/{id}/reject", "stud-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectProfileRequest("CGPA does not match records"))))
                .andExpect(status().isOk());

        StudentProfile p = profile("stud-1");
        assertThat(p.getProfileApprovalStatus()).isEqualTo(ProfileApprovalStatus.REJECTED);
        assertThat(p.getRejectionReason()).isEqualTo("CGPA does not match records");
        assertThat(audits()).extracting(AuditLog::getAction).containsExactly("PROFILE_REJECTED");
        assertThat(email.sent.get(0).body()).contains("CGPA does not match records");
    }

    @Test
    void approve_nonPending_is409() throws Exception {
        seedProfile("stud-1", ProfileApprovalStatus.DRAFT, "CSE", 8.1);
        mockMvc.perform(post("/api/admin/profiles/{id}/approve", "stud-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void reject_nonPending_is409() throws Exception {
        seedProfile("stud-1", ProfileApprovalStatus.APPROVED, "CSE", 8.1);
        mockMvc.perform(post("/api/admin/profiles/{id}/reject", "stud-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectProfileRequest("too late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void edit_correctsCgpa_audits_andLeavesStatusUnchanged() throws Exception {
        seedProfile("stud-1", ProfileApprovalStatus.PENDING_APPROVAL, "CSE", 6.4);

        mockMvc.perform(patch("/api/admin/profiles/{id}", "stud-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AdminEditProfileRequest(null, 7.0, null, null))))
                .andExpect(status().isOk());

        StudentProfile p = profile("stud-1");
        assertThat(p.getAcademic().getCgpa()).isEqualTo(7.0);
        assertThat(p.getProfileApprovalStatus()).isEqualTo(ProfileApprovalStatus.PENDING_APPROVAL); // unchanged
        List<AuditLog> logs = audits();
        assertThat(logs).extracting(AuditLog::getAction).containsExactly("PROFILE_EDITED");
        assertThat(logs.get(0).getOldValue()).contains("cgpa=6.4");
        assertThat(logs.get(0).getNewValue()).contains("cgpa=7.0");
    }

    @Test
    void edit_invalidBranch_is400() throws Exception {
        seedProfile("stud-1", ProfileApprovalStatus.PENDING_APPROVAL, "CSE", 8.1);
        mockMvc.perform(patch("/api/admin/profiles/{id}", "stud-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AdminEditProfileRequest("MECH", null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_returnsOnlyThisTenantsPendingProfiles() throws Exception {
        seedProfile("p1", ProfileApprovalStatus.PENDING_APPROVAL, "CSE", 8.1);
        seedProfile("p2", ProfileApprovalStatus.PENDING_APPROVAL, "ECE", 7.5);
        seedProfile("p3", ProfileApprovalStatus.APPROVED, "CSE", 9.0); // different status

        mockMvc.perform(get("/api/admin/profiles").param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void nonAdmin_isForbidden403() throws Exception {
        seedActiveUser("stud-x", Role.STUDENT, TENANT);
        seedProfile("stud-1", ProfileApprovalStatus.PENDING_APPROVAL, "CSE", 8.1);
        mockMvc.perform(post("/api/admin/profiles/{id}/approve", "stud-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken("stud-x", Role.STUDENT, TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/admin/profiles")).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedAdmin_is403AccountInactive() throws Exception {
        seedActiveUser("dead-admin", Role.COLLEGE_ADMIN, TENANT);
        mongoTemplate.findAll(User.class).stream().filter(u -> "dead-admin".equals(u.getId())).forEach(u -> {
            u.setAccountStatus(AccountStatus.DEACTIVATED);
            mongoTemplate.save(u);
        });
        mockMvc.perform(get("/api/admin/profiles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken("dead-admin", Role.COLLEGE_ADMIN, TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    // ── helpers ──

    private String adminToken() {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.COLLEGE_ADMIN, TENANT);
    }

    private StudentProfile profile(String studentId) {
        return mongoTemplate.findAll(StudentProfile.class).stream()
                .filter(p -> studentId.equals(p.getStudentId())).findFirst().orElseThrow();
    }

    private List<AuditLog> audits() {
        return mongoTemplate.findAll(AuditLog.class);
    }

    private void seedTenant(String tenantId) {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName(tenantId);
        t.setSlug(tenantId);
        t.setBranches(List.of("CSE", "ECE"));
        t.setBatches(List.of("2026", "2027"));
        t.setSeason(new Season(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)));
        t.setStatus(TenantStatus.ACTIVE);
        mongoTemplate.save(t);
    }

    private void seedActiveUser(String id, Role role, String tenantId) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setEmail(id + "@seed.test");
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(AccountStatus.ACTIVE);
        mongoTemplate.save(u);
    }

    private void seedStudent(String id, String email) {
        User u = new User();
        u.setId(id);
        u.setTenantId(TENANT);
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.STUDENT);
        u.setAccountStatus(AccountStatus.ACTIVE);
        mongoTemplate.save(u);
    }

    private void seedProfile(String studentId, ProfileApprovalStatus status, String branch, double cgpa) {
        StudentProfile p = new StudentProfile();
        p.setTenantId(TENANT);
        p.setStudentId(studentId);
        p.setRollNumber("21" + studentId);
        p.setBatch("2026");
        p.getPersonal().setFullName("Student " + studentId);
        p.getPersonal().setPhone("9990000000");
        p.getAcademic().setBranch(branch);
        p.getAcademic().setCgpa(cgpa);
        p.getAcademic().setActiveBacklogs(0);
        p.getPlacement().setSkills(List.of("Java"));
        p.setProfileApprovalStatus(status);
        p.setCompletionPercent(100);
        mongoTemplate.save(p);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
