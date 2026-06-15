package com.campusconnect.student.drives;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.BacklogPolicy;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.EligibilityCriteria;
import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.StudentProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pre-apply transparency: the student drive list grouped Eligible/Applied/Not-Eligible/Closed (Story 5.3, FR-13). */
@SpringBootTest
@Testcontainers
class StudentDriveListTest {

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
    String studentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        mongoTemplate.remove(new Query(), Drive.class);
        mongoTemplate.remove(new Query(), Application.class);
        tenantId = seedTenant("vignan", List.of("CSE", "ECE"), List.of("2026", "2027"));
        studentId = seedUser(tenantId, "s@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
        seedApprovedProfile(); // CSE, cgpa 8.1, batch 2026, 0 backlogs, not placed
    }

    @Test
    void listDrives_groupsIntoAllFourBuckets_withFailedCriteria() throws Exception {
        seedDrive(tenantId, "ELIG", DriveStatus.PUBLISHED, List.of("CSE", "ECE"), futureDeadline());
        String appliedId = seedDrive(tenantId, "APPL", DriveStatus.PUBLISHED, List.of("CSE"), futureDeadline());
        seedApplication(appliedId);
        seedDrive(tenantId, "CLOSED", DriveStatus.CLOSED, List.of("CSE"), futureDeadline());
        seedDrive(tenantId, "NOTELIG", DriveStatus.PUBLISHED, List.of("ECE"), futureDeadline()); // student is CSE

        Map<String, JsonNode> byRole = listByRole(studentToken());

        assertThat(byRole.keySet()).containsExactlyInAnyOrder("ELIG", "APPL", "CLOSED", "NOTELIG");
        assertThat(byRole.get("ELIG").get("group").asText()).isEqualTo("ELIGIBLE");
        assertThat(byRole.get("APPL").get("group").asText()).isEqualTo("APPLIED");
        assertThat(byRole.get("CLOSED").get("group").asText()).isEqualTo("CLOSED");

        JsonNode notElig = byRole.get("NOTELIG");
        assertThat(notElig.get("group").asText()).isEqualTo("NOT_ELIGIBLE");
        assertThat(notElig.get("failedCriteria").get(0).asText()).contains("branch").contains("CSE");

        // The non-NOT_ELIGIBLE buckets carry no criteria (the contract: failedCriteria empty unless not-eligible).
        assertThat(byRole.get("ELIG").get("failedCriteria").size()).isZero();
        assertThat(byRole.get("APPL").get("failedCriteria").size()).isZero();
        assertThat(byRole.get("CLOSED").get("failedCriteria").size()).isZero();
    }

    @Test
    void listDrives_noProfile_everyOpenDriveNotEligible_withProfileReason_no500() throws Exception {
        // The common first-login state: an ACTIVE student with no StudentProfile reaches the list.
        // The engine's null-safety must classify cleanly (not NPE) → NOT_ELIGIBLE, profile-not-approved.
        mongoTemplate.remove(new Query(), StudentProfile.class);
        seedDrive(tenantId, "OPEN", DriveStatus.PUBLISHED, List.of("CSE"), futureDeadline());

        JsonNode node = listByRole(studentToken()).get("OPEN");
        assertThat(node.get("group").asText()).isEqualTo("NOT_ELIGIBLE");
        assertThat(node.get("failedCriteria").get(0).asText()).containsIgnoringCase("profile");
    }

    @Test
    void listDrives_appliedToAClosedDrive_staysApplied_notClosed() throws Exception {
        // APPLIED precedence over CLOSED: a drive the student applied to that has since closed still shows APPLIED.
        String closedAppliedId = seedDrive(tenantId, "APPLCLOSED", DriveStatus.CLOSED, List.of("CSE"), futureDeadline());
        seedApplication(closedAppliedId);

        JsonNode node = listByRole(studentToken()).get("APPLCLOSED");
        assertThat(node.get("group").asText()).isEqualTo("APPLIED");
    }

    @Test
    void listDrives_pastDeadlinePublishedDrive_isClosed() throws Exception {
        seedDrive(tenantId, "EXPIRED", DriveStatus.PUBLISHED, List.of("CSE"), Instant.parse("2020-01-01T00:00:00Z"));

        Map<String, JsonNode> byRole = listByRole(studentToken());

        assertThat(byRole.get("EXPIRED").get("group").asText()).isEqualTo("CLOSED");
    }

    @Test
    void listDrives_cgpaBelowFloor_notEligible_withSpecificReason() throws Exception {
        // Drive floor 9.0 above the student's 8.1 → CGPA_MET fails with a specific reason.
        Drive d = drive(tenantId, "LOWCGPA", DriveStatus.PUBLISHED, List.of("CSE"), futureDeadline());
        d.getEligibility().setMinCgpa(9.0);
        mongoTemplate.save(d);

        JsonNode node = listByRole(studentToken()).get("LOWCGPA");
        assertThat(node.get("group").asText()).isEqualTo("NOT_ELIGIBLE");
        assertThat(node.get("failedCriteria").get(0).asText()).contains("8.1").contains("9.0");
    }

    @Test
    void listDrives_excludesDraftAndOtherTenantDrives() throws Exception {
        seedDrive(tenantId, "VISIBLE", DriveStatus.PUBLISHED, List.of("CSE"), futureDeadline());
        seedDrive(tenantId, "HIDDEN_DRAFT", DriveStatus.DRAFT, List.of("CSE"), futureDeadline());
        String otherTenant = seedTenant("other", List.of("CSE"), List.of("2026"));
        seedDrive(otherTenant, "OTHER_TENANT", DriveStatus.PUBLISHED, List.of("CSE"), futureDeadline());

        Map<String, JsonNode> byRole = listByRole(studentToken());

        assertThat(byRole.keySet()).containsExactly("VISIBLE");
    }

    @Test
    void listDrives_nonStudent_isForbidden403() throws Exception {
        mockMvc.perform(get("/api/student/drives")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken(studentId, Role.RECRUITER, tenantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void listDrives_noToken_is401() throws Exception {
        mockMvc.perform(get("/api/student/drives")).andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    private Map<String, JsonNode> listByRole(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/student/drives").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        Map<String, JsonNode> byRole = new HashMap<>();
        data.forEach(node -> byRole.put(node.get("role").asText(), node));
        return byRole;
    }

    private String studentToken() {
        return "Bearer " + jwtService.issueAccessToken(studentId, Role.STUDENT, tenantId);
    }

    private static Instant futureDeadline() {
        return Instant.now().plusSeconds(7 * 86_400);
    }

    private void seedApprovedProfile() {
        StudentProfile p = new StudentProfile();
        p.setTenantId(tenantId);
        p.setStudentId(studentId);
        p.setRollNumber("21CS001");
        p.setBatch("2026");
        p.getPersonal().setFullName("Asha Rao");
        p.getPersonal().setPhone("9990001234");
        p.getAcademic().setBranch("CSE");
        p.getAcademic().setCgpa(8.1);
        p.getAcademic().setActiveBacklogs(0);
        p.getPlacement().setSkills(List.of("Java"));
        p.setProfileApprovalStatus(ProfileApprovalStatus.APPROVED);
        p.setPlaced(false);
        p.setCompletionPercent(100);
        mongoTemplate.save(p);
    }

    private Drive drive(String tid, String role, DriveStatus status, List<String> branches, Instant deadline) {
        Drive d = new Drive();
        d.setTenantId(tid);
        d.setCreatedBy("rec-1");
        d.setCompanyName("Acme " + role);
        d.setRole(role);
        d.setPackageLpa(12.0);
        d.setLocation("Bengaluru");
        d.setOpenings(3);
        d.setApplyDeadline(deadline);
        EligibilityCriteria e = new EligibilityCriteria();
        e.setBranches(new ArrayList<>(branches));
        e.setMinCgpa(7.0);
        e.setBacklogPolicy(BacklogPolicy.NO_BACKLOG);
        e.setBatch("2026");
        d.setEligibility(e);
        d.setStatus(status);
        return d;
    }

    private String seedDrive(String tid, String role, DriveStatus status, List<String> branches, Instant deadline) {
        return mongoTemplate.save(drive(tid, role, status, branches, deadline)).getId();
    }

    private void seedApplication(String driveId) {
        Application a = new Application();
        a.setTenantId(tenantId);
        a.setStudentId(studentId);
        a.setDriveId(driveId);
        a.setStatus(ApplicationStatus.APPLIED);
        a.setAppliedAt(Instant.now());
        mongoTemplate.save(a);
    }

    private String seedTenant(String slug, List<String> branches, List<String> batches) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(branches);
        t.setBatches(batches);
        t.setSeason(new Season(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)));
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedUser(String tid, String email, Role role, AccountStatus status) {
        User u = new User();
        u.setTenantId(tid);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAccountStatus(status);
        return userRepository.save(u).getId();
    }
}
