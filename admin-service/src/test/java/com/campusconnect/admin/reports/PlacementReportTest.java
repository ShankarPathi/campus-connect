package com.campusconnect.admin.reports;

import com.campusconnect.common.domain.AccountStatus;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Placement reports — overall/branch/company breakdowns + CSV export + tenant isolation + auth (Story 8.5, FR-26). */
@SpringBootTest
@Testcontainers
class PlacementReportTest {

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
        mongoTemplate.remove(new Query(), PlacementRecord.class);
        seedTenant(TENANT);
        seedActiveUser("admin-1", Role.COLLEGE_ADMIN, TENANT);
    }

    /**
     * Fixture (tenant-a): 5 students — 3 CSE (s1,s2,s3), 2 ECE (s4,s5).
     * OFFICIALLY_PLACED: s1@"Acme, Inc"(CSE), s1@"Globex"(CSE, 2nd placement), s2@"Globex"(CSE), s4@"Globex"(ECE).
     * PENDING_CONFIRMATION (excluded): s3@"Initech"(CSE). s5 unplaced.
     * => distinct placed = {s1,s2,s4}=3; overall 3/5=60.0%; CSE 2/3=66.7%; ECE 1/2=50.0%.
     *    company-wise (per placement): Globex=3, "Acme, Inc"=1. CSV = 4 placement rows.
     */
    private void seedReportFixture(String tid) {
        seedProfile(tid, "s1", "CSE");
        seedProfile(tid, "s2", "CSE");
        seedProfile(tid, "s3", "CSE");
        seedProfile(tid, "s4", "ECE");
        seedProfile(tid, "s5", "ECE");
        seedPlaced(tid, "s1", "Acme, Inc", PlacementStatus.OFFICIALLY_PLACED);
        seedPlaced(tid, "s1", "Globex", PlacementStatus.OFFICIALLY_PLACED);
        seedPlaced(tid, "s2", "Globex", PlacementStatus.OFFICIALLY_PLACED);
        seedPlaced(tid, "s4", "Globex", PlacementStatus.OFFICIALLY_PLACED);
        seedPlaced(tid, "s3", "Initech", PlacementStatus.PENDING_CONFIRMATION);
    }

    @Test
    void report_overallBranchAndCompany_tenantScoped() throws Exception {
        seedReportFixture(TENANT);
        seedTenant(OTHER);
        seedReportFixture(OTHER); // a second tenant — must not bleed in

        mockMvc.perform(get("/api/admin/reports/placements").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.totalStudents").value(5))
                .andExpect(jsonPath("$.data.overall.placedStudents").value(3))   // distinct, not 4 records
                .andExpect(jsonPath("$.data.overall.placementPercent").value(60.0))
                // branch-wise, sorted most-placed first → CSE then ECE
                .andExpect(jsonPath("$.data.branchwise[0].branch").value("CSE"))
                .andExpect(jsonPath("$.data.branchwise[0].totalStudents").value(3))
                .andExpect(jsonPath("$.data.branchwise[0].placedStudents").value(2))
                .andExpect(jsonPath("$.data.branchwise[0].placementPercent").value(66.7))
                .andExpect(jsonPath("$.data.branchwise[1].branch").value("ECE"))
                .andExpect(jsonPath("$.data.branchwise[1].placedStudents").value(1))
                .andExpect(jsonPath("$.data.branchwise[1].placementPercent").value(50.0))
                // company-wise (per placement), sorted desc → Globex(3) then Acme, Inc(1)
                .andExpect(jsonPath("$.data.companywise[0].company").value("Globex"))
                .andExpect(jsonPath("$.data.companywise[0].placements").value(3))
                .andExpect(jsonPath("$.data.companywise[1].company").value("Acme, Inc"))
                .andExpect(jsonPath("$.data.companywise[1].placements").value(1));
    }

    @Test
    void report_emptyTenant_zeroPercent_noDivideByZero() throws Exception {
        mockMvc.perform(get("/api/admin/reports/placements").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.totalStudents").value(0))
                .andExpect(jsonPath("$.data.overall.placedStudents").value(0))
                .andExpect(jsonPath("$.data.overall.placementPercent").value(0.0))
                .andExpect(jsonPath("$.data.branchwise.length()").value(0))
                .andExpect(jsonPath("$.data.companywise.length()").value(0));
    }

    @Test
    void csvExport_detailedPlacedList_escapedAndTenantScoped() throws Exception {
        seedReportFixture(TENANT);
        seedPlaced(TENANT, "s5", "=HACK()", PlacementStatus.OFFICIALLY_PLACED); // formula-injection probe (5th placed row)
        seedTenant(OTHER);
        seedProfile(OTHER, "x1", "CSE");
        seedPlaced(OTHER, "x1", "OtherCorp", PlacementStatus.OFFICIALLY_PLACED);

        MvcResult res = mockMvc.perform(get("/api/admin/reports/placements/export").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"placements-tenant-a.csv\""))
                .andReturn();

        String csv = res.getResponse().getContentAsString();
        String[] lines = csv.strip().split("\n");
        assertThat(lines[0]).isEqualTo("rollNumber,name,branch,company,ctc,role,joiningDate"); // header
        assertThat(lines).hasSize(1 + 5); // header + 5 OFFICIALLY_PLACED rows (per placement)
        assertThat(csv).contains("\"Acme, Inc\"");      // the comma-bearing company is quoted
        assertThat(csv).contains("'=HACK()");           // formula-injection neutralized with a leading apostrophe
        assertThat(csv).doesNotContain("OtherCorp");    // tenant-b's placement is not in tenant-a's CSV
        assertThat(csv).doesNotContain("Initech");      // PENDING_CONFIRMATION excluded
    }

    @Test
    void nonAdmin_is403() throws Exception {
        seedActiveUser("stud-x", Role.STUDENT, TENANT);
        mockMvc.perform(get("/api/admin/reports/placements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken("stud-x", Role.STUDENT, TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/admin/reports/placements")).andExpect(status().isUnauthorized());
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

    private void seedProfile(String tid, String studentId, String branch) {
        StudentProfile p = new StudentProfile();
        p.setTenantId(tid);
        p.setStudentId(tid + "-" + studentId);
        p.setRollNumber("ROLL-" + tid + "-" + studentId);
        p.setBatch("2026");
        p.getPersonal().setFullName("Name " + studentId);
        p.getPersonal().setPhone("9990000000");
        p.getAcademic().setBranch(branch);
        p.getAcademic().setCgpa(8.0);
        p.getAcademic().setActiveBacklogs(0);
        p.getPlacement().setSkills(List.of("Java"));
        p.setProfileApprovalStatus(ProfileApprovalStatus.APPROVED);
        p.setCompletionPercent(100);
        mongoTemplate.save(p);
    }

    private void seedPlaced(String tid, String studentId, String company, PlacementStatus status) {
        int n = ++seq;
        PlacementRecord r = new PlacementRecord();
        r.setTenantId(tid);
        r.setStudentId(tid + "-" + studentId);
        r.setApplicationId("app-" + n); // distinct per record (unique {tenant, applicationId})
        r.setCompany(company);
        r.setCtc(12.5);
        r.setRole("SDE-1");
        r.setJoiningDate(Instant.parse("2026-07-01T00:00:00Z"));
        r.setStatus(status);
        mongoTemplate.save(r);
    }
}
