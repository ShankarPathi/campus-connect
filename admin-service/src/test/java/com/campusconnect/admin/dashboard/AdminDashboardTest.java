package com.campusconnect.admin.dashboard;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.PlacementRecord;
import com.campusconnect.common.domain.PlacementStatus;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
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

/** College-Admin season snapshot — the 7 tenant-scoped figures + isolation + auth gates (Story 8.4, FR-27). */
@SpringBootTest
@Testcontainers
class AdminDashboardTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private static final String TENANT = "tenant-a";
    private static final String OTHER = "tenant-b";
    private int seq;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), Application.class);
        mongoTemplate.remove(new Query(), PlacementRecord.class);
        seedTenant(TENANT);
        seedActiveUser("admin-1", Role.COLLEGE_ADMIN, TENANT);
    }

    @Test
    void snapshot_returnsTenantScopedCounts() throws Exception {
        seedFixture(TENANT);
        // a second tenant with its own data — must NOT bleed into tenant-a's figures
        seedTenant(OTHER);
        seedFixture(OTHER);

        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingProfileApprovals").value(2))
                .andExpect(jsonPath("$.data.pendingRecruiterApprovals").value(2))
                .andExpect(jsonPath("$.data.pendingDriveApprovals").value(3))
                .andExpect(jsonPath("$.data.totalStudents").value(4))
                .andExpect(jsonPath("$.data.totalDrives").value(5))
                .andExpect(jsonPath("$.data.totalApplications").value(6))
                .andExpect(jsonPath("$.data.placedStudents").value(2));
    }

    @Test
    void snapshot_emptyTenant_isAllZero() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingProfileApprovals").value(0))
                .andExpect(jsonPath("$.data.pendingRecruiterApprovals").value(0))
                .andExpect(jsonPath("$.data.pendingDriveApprovals").value(0))
                .andExpect(jsonPath("$.data.totalStudents").value(0))
                .andExpect(jsonPath("$.data.totalDrives").value(0))
                .andExpect(jsonPath("$.data.totalApplications").value(0))
                .andExpect(jsonPath("$.data.placedStudents").value(0));
    }

    @Test
    void placedStudents_countsDistinctStudents_notRecords() throws Exception {
        // One student with TWO OFFICIALLY_PLACED records (placed via two applications) must count as 1.
        String student = "stud-twice";
        seedPlacementFor(TENANT, student, PlacementStatus.OFFICIALLY_PLACED);
        seedPlacementFor(TENANT, student, PlacementStatus.OFFICIALLY_PLACED);
        seedPlacementFor(TENANT, "stud-other", PlacementStatus.OFFICIALLY_PLACED);

        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placedStudents").value(2)); // 2 distinct students, not 3 records
    }

    @Test
    void nonAdmin_is403() throws Exception {
        seedActiveUser("stud-x", Role.STUDENT, TENANT);
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken("stud-x", Role.STUDENT, TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    // ── fixture: exactly 2 pending profiles, 2 pending recruiters, 3 pending drives (5 total),
    //    4 students, 6 applications, 2 OFFICIALLY_PLACED (of 3 placement records) ──
    private void seedFixture(String tid) {
        seedProfile(tid, ProfileApprovalStatus.PENDING_APPROVAL);
        seedProfile(tid, ProfileApprovalStatus.PENDING_APPROVAL);
        seedProfile(tid, ProfileApprovalStatus.APPROVED);
        seedProfile(tid, ProfileApprovalStatus.REJECTED);

        seedUser(tid, Role.RECRUITER, AccountStatus.PENDING_APPROVAL);
        seedUser(tid, Role.RECRUITER, AccountStatus.PENDING_APPROVAL);
        seedUser(tid, Role.RECRUITER, AccountStatus.ACTIVE);

        seedDrive(tid, DriveStatus.PENDING_APPROVAL);
        seedDrive(tid, DriveStatus.PENDING_APPROVAL);
        seedDrive(tid, DriveStatus.PENDING_APPROVAL);
        seedDrive(tid, DriveStatus.PUBLISHED);
        seedDrive(tid, DriveStatus.DRAFT);

        for (int i = 0; i < 4; i++) {
            seedUser(tid, Role.STUDENT, AccountStatus.ACTIVE);
        }

        for (int i = 0; i < 6; i++) {
            seedApplication(tid);
        }

        seedPlacement(tid, PlacementStatus.OFFICIALLY_PLACED);
        seedPlacement(tid, PlacementStatus.OFFICIALLY_PLACED);
        seedPlacement(tid, PlacementStatus.PENDING_CONFIRMATION);
    }

    // ── helpers ──

    private String adminToken() {
        return "Bearer " + jwtService.issueAccessToken("admin-1", Role.COLLEGE_ADMIN, TENANT);
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

    private void seedUser(String tid, Role role, AccountStatus status) {
        int n = ++seq;
        User u = new User();
        u.setTenantId(tid);
        u.setEmail("u" + n + "@" + tid + ".test");
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(status);
        mongoTemplate.save(u);
    }

    private void seedProfile(String tid, ProfileApprovalStatus status) {
        int n = ++seq;
        StudentProfile p = new StudentProfile();
        p.setTenantId(tid);
        p.setStudentId("stud-" + tid + "-" + n);
        p.setRollNumber("21-" + n);
        p.setBatch("2026");
        p.getPersonal().setFullName("Student " + n);
        p.getPersonal().setPhone("9990000000");
        p.getAcademic().setBranch("CSE");
        p.getAcademic().setCgpa(8.0);
        p.getAcademic().setActiveBacklogs(0);
        p.getPlacement().setSkills(List.of("Java"));
        p.setProfileApprovalStatus(status);
        p.setCompletionPercent(100);
        mongoTemplate.save(p);
    }

    private void seedDrive(String tid, DriveStatus status) {
        int n = ++seq;
        Drive d = new Drive();
        d.setTenantId(tid);
        d.setCreatedBy("rec-" + n);
        d.setCompanyName("Acme " + n);
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
        d.setStatus(status);
        mongoTemplate.save(d);
    }

    private void seedApplication(String tid) {
        int n = ++seq;
        Application a = new Application();
        a.setTenantId(tid);
        a.setStudentId("stud-" + tid + "-" + n);
        a.setDriveId("drive-" + n);
        a.setStatus(ApplicationStatus.APPLIED);
        a.setAppliedAt(Instant.now());
        a.setResumeSnapshotKey("resumes/v1.pdf");
        mongoTemplate.save(a);
    }

    private void seedPlacement(String tid, PlacementStatus status) {
        seedPlacementFor(tid, "stud-" + tid + "-" + (++seq), status);
    }

    /** A placement record for an explicit student — one record per application (distinct applicationId). */
    private void seedPlacementFor(String tid, String studentId, PlacementStatus status) {
        int n = ++seq;
        PlacementRecord r = new PlacementRecord();
        r.setTenantId(tid);
        r.setStudentId(studentId);
        r.setApplicationId("app-" + n);
        r.setStatus(status);
        mongoTemplate.save(r);
    }
}
