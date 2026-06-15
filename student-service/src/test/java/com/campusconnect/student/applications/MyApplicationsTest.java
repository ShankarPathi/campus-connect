package com.campusconnect.student.applications;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.BacklogPolicy;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Track my applications: own list, drive context, newest-first, owner/tenant exclusion (Story 5.6, FR-17). */
@SpringBootTest
@Testcontainers
class MyApplicationsTest {

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), Application.class);
        tenantId = seedTenant("vignan");
        studentId = seedUser(tenantId, "s@v.edu", Role.STUDENT);
    }

    @Test
    void list_returnsOwnApplications_withDriveContext_newestFirst() throws Exception {
        String driveA = seedDrive(tenantId, "Acme", "SDE-1");
        String driveB = seedDrive(tenantId, "Globex", "Analyst");
        // older application first in seed order; newer second — the response must invert to newest-first.
        seedApplication(tenantId, studentId, driveA, ApplicationStatus.WITHDRAWN, Instant.parse("2026-06-01T00:00:00Z"));
        seedApplication(tenantId, studentId, driveB, ApplicationStatus.APPLIED, Instant.parse("2026-06-10T00:00:00Z"));

        mockMvc.perform(get("/api/student/applications").header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // newest-first: driveB (Globex, APPLIED) precedes driveA (Acme, WITHDRAWN)
                .andExpect(jsonPath("$.data[0].driveId").value(driveB))
                .andExpect(jsonPath("$.data[0].companyName").value("Globex"))
                .andExpect(jsonPath("$.data[0].role").value("Analyst"))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data[1].driveId").value(driveA))
                .andExpect(jsonPath("$.data[1].companyName").value("Acme"))
                .andExpect(jsonPath("$.data[1].status").value("WITHDRAWN"));
    }

    @Test
    void list_excludesOtherStudentsAndOtherTenants() throws Exception {
        String drive = seedDrive(tenantId, "Acme", "SDE-1");
        seedApplication(tenantId, studentId, drive, ApplicationStatus.APPLIED, Instant.now());

        String otherStudent = seedUser(tenantId, "other@v.edu", Role.STUDENT);
        seedApplication(tenantId, otherStudent, drive, ApplicationStatus.APPLIED, Instant.now()); // same tenant, other owner
        String otherTenant = seedTenant("other");
        seedApplication(otherTenant, studentId, drive, ApplicationStatus.APPLIED, Instant.now()); // other tenant, same id

        mockMvc.perform(get("/api/student/applications").header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
    }

    @Test
    void list_applicationWithMissingDrive_rendersWithNullCompany_no500() throws Exception {
        // Defensive branch: if an application's drive is absent from the batch load, the item still
        // renders (null company/role) — ApplicationResponse.of is null-Drive-safe, no NPE/500.
        seedApplication(tenantId, studentId, "missing-drive-id", ApplicationStatus.APPLIED, Instant.now());

        mockMvc.perform(get("/api/student/applications").header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].driveId").value("missing-drive-id"))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data[0].companyName").doesNotExist())
                .andExpect(jsonPath("$.data[0].role").doesNotExist());
    }

    @Test
    void list_noApplications_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/student/applications").header(HttpHeaders.AUTHORIZATION, token(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void list_nonStudent_403() throws Exception {
        mockMvc.perform(get("/api/student/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken(studentId, Role.RECRUITER, tenantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void list_noToken_401() throws Exception {
        mockMvc.perform(get("/api/student/applications")).andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    private String token(String userId) {
        return "Bearer " + jwtService.issueAccessToken(userId, Role.STUDENT, tenantId);
    }

    private String seedApplication(String tid, String sid, String did, ApplicationStatus status, Instant appliedAt) {
        Application a = new Application();
        a.setTenantId(tid);
        a.setStudentId(sid);
        a.setDriveId(did);
        a.setStatus(status);
        a.setAppliedAt(appliedAt);
        a.setResumeSnapshotKey("resumes/v1.pdf");
        return mongoTemplate.save(a).getId();
    }

    private String seedDrive(String tid, String company, String role) {
        Drive d = new Drive();
        d.setTenantId(tid);
        d.setCreatedBy("rec-1");
        d.setCompanyName(company);
        d.setRole(role);
        d.setPackageLpa(12.0);
        d.setLocation("Bengaluru");
        d.setOpenings(3);
        d.setApplyDeadline(Instant.now().plusSeconds(7 * 86_400));
        EligibilityCriteria e = new EligibilityCriteria();
        e.setBranches(new ArrayList<>(List.of("CSE")));
        e.setMinCgpa(7.0);
        e.setBacklogPolicy(BacklogPolicy.NO_BACKLOG);
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
