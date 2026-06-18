package com.campusconnect.recruiter.applications;

import com.campusconnect.common.domain.AcademicDetails;
import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.PersonalDetails;
import com.campusconnect.common.domain.PlacementDetails;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.StudentProfile;
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
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recruiter applicant review (Story 6.1, FR-18 / NFR-3): own-drive list, PII minimization, filter/search/sort/page, ownership 404, authz. */
@SpringBootTest
@Testcontainers
class ApplicantReviewTest {

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
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
        tenantId = seedTenant("vignan", List.of("CSE", "ECE"), List.of("2026", "2027"));
        recruiterId = seedRecruiter("hr@acme.com", AccountStatus.ACTIVE);
        driveId = seedDrive(tenantId, recruiterId);
    }

    // ── list + PII minimization ──

    @Test
    void list_ownDrive_returnsMinimizedRows_andHidesRestrictedPII() throws Exception {
        seedApplicant("alice", "Alice A", "R-001", "CSE", 8.5, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-01T00:00:00Z"));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Alice A"))
                .andExpect(jsonPath("$.data.items[0].phone").value("9999999999")) // kept (Decision C)
                .andExpect(jsonPath("$.data.items[0].rollNumber").value("R-001"))
                .andExpect(jsonPath("$.data.items[0].branch").value("CSE"))
                .andExpect(jsonPath("$.data.items[0].cgpa").value(8.5))
                .andExpect(jsonPath("$.data.items[0].status").value("APPLIED"))
                // restricted PII must never be serialized
                .andExpect(jsonPath("$.data.items[0].address").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].gender").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].resumeSnapshotKey").doesNotExist());
    }

    @Test
    void list_emptyDrive_returnsEmptyPage_not404() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    // ── status filter + WITHDRAWN default exclusion ──

    @Test
    void list_excludesWithdrawnByDefault_butStatusFilterSurfacesThem() throws Exception {
        seedApplicant("alice", "Alice A", "R-001", "CSE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-01T00:00:00Z"));
        seedApplicant("bob", "Bob B", "R-002", "ECE", 7.0, 1, ApplicationStatus.WITHDRAWN, Instant.parse("2026-06-02T00:00:00Z"));
        // default hides the withdrawn applicant
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Alice A"));
        // explicit filter brings them back
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("status", "WITHDRAWN")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Bob B"));
    }

    @Test
    void list_statusFilter_narrowsToGivenStatus() throws Exception {
        seedApplicant("alice", "Alice A", "R-001", "CSE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-01T00:00:00Z"));
        seedApplicant("bob", "Bob B", "R-002", "ECE", 7.0, 0, ApplicationStatus.SHORTLISTED, Instant.parse("2026-06-02T00:00:00Z"));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("status", "SHORTLISTED")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Bob B"));
    }

    // ── search ──

    @Test
    void list_search_matchesNameOrRoll_caseInsensitive() throws Exception {
        seedApplicant("alice", "Alice Anand", "R-001", "CSE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-01T00:00:00Z"));
        seedApplicant("bob", "Bob Bose", "R-777", "ECE", 7.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-02T00:00:00Z"));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("search", "alice")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Alice Anand"));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("search", "R-777")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Bob Bose"));
    }

    // ── sort ──

    @Test
    void list_sortByCgpaDesc_ordersHighestFirst() throws Exception {
        seedApplicant("low", "Low Cgpa", "R-001", "CSE", 6.5, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-01T00:00:00Z"));
        seedApplicant("high", "High Cgpa", "R-002", "ECE", 9.2, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-02T00:00:00Z"));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("sortBy", "cgpa").param("sortDir", "desc")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fullName").value("High Cgpa"))
                .andExpect(jsonPath("$.data.items[1].fullName").value("Low Cgpa"));
    }

    @Test
    void list_defaultSort_isAppliedAtNewestFirst() throws Exception {
        seedApplicant("older", "Older Apply", "R-001", "CSE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-01T00:00:00Z"));
        seedApplicant("newer", "Newer Apply", "R-002", "ECE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-09T00:00:00Z"));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fullName").value("Newer Apply"))
                .andExpect(jsonPath("$.data.items[1].fullName").value("Older Apply"));
    }

    // ── pagination ──

    @Test
    void list_paginates() throws Exception {
        seedApplicant("a", "A One", "R-001", "CSE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-03T00:00:00Z"));
        seedApplicant("b", "B Two", "R-002", "ECE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-02T00:00:00Z"));
        seedApplicant("c", "C Three", "R-003", "CSE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-01T00:00:00Z"));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("page", "0").param("pageSize", "2")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("page", "1").param("pageSize", "2")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void list_hugePage_returnsEmptyPage_no500() throws Exception {
        // a page index large enough to overflow page*pageSize in int math must still yield a clean empty tail, not 500
        seedApplicant("alice", "Alice A", "R-001", "CSE", 8.0, 0, ApplicationStatus.APPLIED, Instant.parse("2026-06-01T00:00:00Z"));
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("page", "2000000000").param("pageSize", "2")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void list_invalidStatusValue_is400() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).param("status", "BOGUS")
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isBadRequest());
    }

    // ── ownership 404 ──

    @Test
    void list_otherRecruitersDrive_is404() throws Exception {
        String otherRecruiter = seedRecruiter("hr2@beta.com", AccountStatus.ACTIVE);
        String otherDrive = seedDrive(tenantId, otherRecruiter); // same tenant, different owner
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", otherDrive).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void list_otherTenantsDrive_is404() throws Exception {
        String otherTenant = seedTenant("other", List.of("CSE"), List.of("2026"));
        String foreignDrive = seedDrive(otherTenant, "ghost-recruiter");
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", foreignDrive).header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound());
    }

    // ── authz ──

    @Test
    void list_noToken_is401() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId)).andExpect(status().isUnauthorized());
    }

    @Test
    void list_studentToken_is403() throws Exception {
        String student = seedUser("stud@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).header(HttpHeaders.AUTHORIZATION, token(student, Role.STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void list_adminToken_is403() throws Exception {
        String admin = seedUser("dean@v.edu", Role.COLLEGE_ADMIN, AccountStatus.ACTIVE);
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants", driveId).header(HttpHeaders.AUTHORIZATION, token(admin, Role.COLLEGE_ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── helpers ──

    private void seedApplicant(String studentId, String fullName, String roll, String branch, Double cgpa,
                               Integer backlogs, ApplicationStatus status, Instant appliedAt) {
        StudentProfile p = new StudentProfile();
        p.setTenantId(tenantId);
        p.setStudentId(studentId);
        p.setRollNumber(roll);
        p.setBatch("2026");
        PersonalDetails pd = new PersonalDetails();
        pd.setFullName(fullName);
        pd.setPhone("9999999999");
        pd.setAddress("12 Secret Lane, Confidential City"); // restricted — must not surface
        pd.setDateOfBirth("2003-05-01");                    // restricted
        pd.setGender("F");                                  // restricted
        p.setPersonal(pd);
        AcademicDetails ad = new AcademicDetails();
        ad.setBranch(branch);
        ad.setCgpa(cgpa);
        ad.setActiveBacklogs(backlogs);
        p.setAcademic(ad);
        PlacementDetails pl = new PlacementDetails();
        pl.setSkills(new ArrayList<>(List.of("Java", "Spring")));
        p.setPlacement(pl);
        mongoTemplate.save(p);

        Application a = new Application();
        a.setTenantId(tenantId);
        a.setStudentId(studentId);
        a.setDriveId(driveId);
        a.setStatus(status);
        a.setAppliedAt(appliedAt);
        a.setResumeSnapshotKey("resumes/" + tenantId + "/" + studentId + "/snap.pdf");
        mongoTemplate.save(a);
    }

    private String token(String userId, Role role) {
        return "Bearer " + jwtService.issueAccessToken(userId, role, tenantId);
    }

    private String seedTenant(String slug, List<String> branches, List<String> batches) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(branches);
        t.setBatches(batches);
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
