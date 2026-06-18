package com.campusconnect.student.profile;

import com.campusconnect.common.domain.AccountStatus;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Build/submit own placement profile (Story 3.1, FR-7): completion, submit gate, state guards, ownership, authz. */
@SpringBootTest
@Testcontainers
class StudentProfileTest {

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
    String studentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), StudentProfile.class);
        tenantId = seedTenant("vignan", List.of("CSE", "ECE"), List.of("2026", "2027"));
        studentId = seedUser(tenantId, "s@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
    }

    // ── get / save / completion ──

    @Test
    void getMyProfile_whenNone_returnsEmptyDraft() throws Exception {
        mockMvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileApprovalStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.completionPercent").value(0));
    }

    @Test
    void save_partial_staysDraft_withPartialCompletion() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personal", Map.of("fullName", "Asha", "phone", "9990001234"));
        body.put("rollNumber", "21CS001");
        body.put("batch", "2026");
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileApprovalStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.completionPercent").value(50)); // 4 of 8 required fields
    }

    @Test
    void save_full_is100() throws Exception {
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("CSE", "2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completionPercent").value(100));
    }

    @Test
    void save_invalidBranch_is400Validation() throws Exception {
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("MECH", "2026"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void save_invalidBatch_is400Validation() throws Exception {
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("CSE", "1999"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void save_cgpaOutOfRange_is400Validation() throws Exception {
        Map<String, Object> body = fullProfile("CSE", "2026");
        @SuppressWarnings("unchecked")
        Map<String, Object> academic = (Map<String, Object>) body.get("academic");
        academic.put("cgpa", 11.0); // > 10
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── submit ──

    @Test
    void submit_incompleteProfile_is400ProfileIncomplete() throws Exception {
        // save only a partial profile, then submit
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personal", Map.of("fullName", "Asha", "phone", "9990001234"));
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));

        mockMvc.perform(post("/api/student/profile/submit").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROFILE_INCOMPLETE"));
    }

    @Test
    void submit_completeProfile_movesToPendingApproval() throws Exception {
        saveFull();
        mockMvc.perform(post("/api/student/profile/submit").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileApprovalStatus").value("PENDING_APPROVAL"));
    }

    @Test
    void submit_alreadyPending_is409() throws Exception {
        saveFull();
        mockMvc.perform(post("/api/student/profile/submit").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk());
        // second submit
        mockMvc.perform(post("/api/student/profile/submit").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void edit_afterSubmit_is409() throws Exception {
        saveFull();
        mockMvc.perform(post("/api/student/profile/submit").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk());
        // editing a PENDING_APPROVAL profile is blocked
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("ECE", "2027"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    // ── ownership ──

    @Test
    void twoStudents_haveIndependentProfiles() throws Exception {
        String other = seedUser(tenantId, "other@v.edu", Role.STUDENT, AccountStatus.ACTIVE);
        saveFull(); // student "studentId" → full CSE profile

        // the other student starts empty (never sees studentId's profile)
        mockMvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(other, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completionPercent").value(0))
                .andExpect(jsonPath("$.data.rollNumber").doesNotExist());
    }

    // ── rejection reason + re-submit (Story 3.3) ──

    @Test
    void getProfile_whenRejected_exposesRejectionReason() throws Exception {
        seedRejectedProfile("CGPA mismatch — please correct");
        mockMvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileApprovalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("CGPA mismatch — please correct"));
    }

    @Test
    void submit_whenRejected_movesToPending_andClearsReason() throws Exception {
        seedRejectedProfile("CGPA mismatch");
        mockMvc.perform(post("/api/student/profile/submit").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileApprovalStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.rejectionReason").doesNotExist());
    }

    @Test
    void edit_whenRejected_isAllowed() throws Exception {
        seedRejectedProfile("fix it");
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("ECE", "2027"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completionPercent").value(100));
    }

    // ── season edit-lock (Story 3.4): independent of approval ──

    @Test
    void edit_whenLocked_is409ProfileLocked_evenWhenApproved() throws Exception {
        seedLockedProfile(ProfileApprovalStatus.APPROVED); // locked + APPROVED → lock wins over status
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("ECE", "2027"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_LOCKED"));
    }

    @Test
    void submit_whenLocked_is409ProfileLocked() throws Exception {
        seedLockedProfile(ProfileApprovalStatus.REJECTED); // editable status, but frozen by the lock
        mockMvc.perform(post("/api/student/profile/submit").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_LOCKED"));
    }

    @Test
    void getProfile_whenLocked_exposesIsLocked_andStatusUnchanged() throws Exception {
        seedLockedProfile(ProfileApprovalStatus.APPROVED);
        mockMvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLocked").value(true))
                .andExpect(jsonPath("$.data.profileApprovalStatus").value("APPROVED")); // two independent fields
    }

    @Test
    void edit_whenLockedDraft_is409ProfileLocked() throws Exception {
        // DRAFT is an editable status, so here the lock is the SOLE blocker (not the isEditable guard)
        seedLockedProfile(ProfileApprovalStatus.DRAFT);
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("ECE", "2027"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_LOCKED"));
    }

    @Test
    void edit_whenUnlocked_stillWorks() throws Exception {
        // regression: an unlocked draft is editable (the happy path is not broken by the lock guard)
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("CSE", "2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLocked").value(false));
    }

    // ── authz ──

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/student/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void recruiterToken_is403Forbidden() throws Exception {
        String recruiter = seedUser(tenantId, "hr@v.edu", Role.RECRUITER, AccountStatus.ACTIVE);
        mockMvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(recruiter, Role.RECRUITER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void deactivatedStudent_is403AccountInactive() throws Exception {
        String dead = seedUser(tenantId, "dead@v.edu", Role.STUDENT, AccountStatus.DEACTIVATED);
        mockMvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(dead, Role.STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    // ── helpers ──

    private void saveFull() throws Exception {
        mockMvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, token(studentId, Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(json(fullProfile("CSE", "2026"))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> fullProfile(String branch, String batch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personal", new LinkedHashMap<>(Map.of("fullName", "Asha Rao", "phone", "9990001234")));
        body.put("academic", new LinkedHashMap<>(Map.of("branch", branch, "cgpa", 8.1, "activeBacklogs", 0)));
        body.put("placement", new LinkedHashMap<>(Map.of("skills", List.of("Java", "SQL"))));
        body.put("rollNumber", "21CS001");
        body.put("batch", batch);
        return body;
    }

    /** Seeds a complete REJECTED profile (with a reason) for the test student, directly via Mongo. */
    private void seedRejectedProfile(String reason) {
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
        p.setProfileApprovalStatus(ProfileApprovalStatus.REJECTED);
        p.setRejectionReason(reason);
        p.setCompletionPercent(100);
        mongoTemplate.save(p);
    }

    /** Seeds a complete, LOCKED profile in the given approval status for the test student (Story 3.4). */
    private void seedLockedProfile(ProfileApprovalStatus status) {
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
        p.setProfileApprovalStatus(status);
        p.setLocked(true);
        p.setCompletionPercent(100);
        mongoTemplate.save(p);
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
