package com.campusconnect.student.applications;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Withdraw an application: pre-shortlist transition + the post-shortlist block + owner isolation (Story 5.5, FR-16). */
@SpringBootTest
@Testcontainers
class WithdrawTest {

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
    String tenantId;
    String studentId;
    String driveId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), Application.class);
        tenantId = seedTenant("vignan");
        studentId = seedUser(tenantId, "s@v.edu", Role.STUDENT);
        driveId = seedDrive(tenantId);
    }

    @Test
    void withdraw_applied_200_marksWithdrawn() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/student/applications/{id}/withdraw", appId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    void withdraw_shortlisted_409_statusUnchanged() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.SHORTLISTED);
        mockMvc.perform(post("/api/student/applications/{id}/withdraw", appId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WITHDRAW_NOT_ALLOWED"));

        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
    }

    @Test
    void withdraw_underReview_200_marksWithdrawn() throws Exception {
        // The second allowed source state (pre-shortlist) — withdrawable end-to-end at the HTTP layer.
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.UNDER_REVIEW);
        mockMvc.perform(post("/api/student/applications/{id}/withdraw", appId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    void withdraw_alreadyWithdrawn_409_statusUnchanged() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.WITHDRAWN);
        mockMvc.perform(post("/api/student/applications/{id}/withdraw", appId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WITHDRAW_NOT_ALLOWED"));

        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    void staleVersionSave_throwsOptimisticLock() {
        // The @Version backstop: a concurrent modification (stale version) fails the save at the DB —
        // GlobalExceptionHandler maps this to a clean 409 (proven in GlobalExceptionHandlerTest).
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.APPLIED);
        Application a = mongoTemplate.findById(appId, Application.class);
        Application stale = mongoTemplate.findById(appId, Application.class); // both at version 0

        a.setStatus(ApplicationStatus.WITHDRAWN);
        mongoTemplate.save(a); // version 0 → 1

        stale.setStatus(ApplicationStatus.WITHDRAWN);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mongoTemplate.save(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void withdraw_anotherStudentsApplication_404() throws Exception {
        String otherStudent = seedUser(tenantId, "other@v.edu", Role.STUDENT);
        String appId = seedApplication(tenantId, otherStudent, ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/student/applications/{id}/withdraw", appId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(mongoTemplate.findById(appId, Application.class).getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    void withdraw_crossTenantApplication_404() throws Exception {
        String otherTenant = seedTenant("other");
        // Same studentId value, different tenant — the tenant-scoped load must not reach it.
        String appId = seedApplication(otherTenant, studentId, ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/student/applications/{id}/withdraw", appId).header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void withdraw_missingApplication_404() throws Exception {
        mockMvc.perform(post("/api/student/applications/{id}/withdraw", "nonexistent").header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void withdraw_nonStudent_403() throws Exception {
        String appId = seedApplication(tenantId, studentId, ApplicationStatus.APPLIED);
        mockMvc.perform(post("/api/student/applications/{id}/withdraw", appId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken(studentId, Role.RECRUITER, tenantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── helpers ──

    private String token(String userId) {
        return "Bearer " + jwtService.issueAccessToken(userId, Role.STUDENT, tenantId);
    }

    private String seedApplication(String tid, String sid, ApplicationStatus status) {
        Application a = new Application();
        a.setTenantId(tid);
        a.setStudentId(sid);
        a.setDriveId(driveId);
        a.setStatus(status);
        a.setAppliedAt(Instant.now());
        a.setResumeSnapshotKey("resumes/v1.pdf");
        return mongoTemplate.save(a).getId();
    }

    private String seedDrive(String tid) {
        Drive d = new Drive();
        d.setTenantId(tid);
        d.setCreatedBy("rec-1");
        d.setCompanyName("Acme Corp");
        d.setRole("SDE-1");
        d.setPackageLpa(12.0);
        d.setLocation("Bengaluru");
        d.setOpenings(3);
        d.setApplyDeadline(Instant.now().plusSeconds(7 * 86_400));
        EligibilityCriteria e = new EligibilityCriteria();
        e.setBranches(new ArrayList<>(List.of("CSE")));
        e.setMinCgpa(7.0);
        e.setBatch("2026");
        d.setEligibility(e);
        d.setStatus(DriveStatus.PUBLISHED);
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
