package com.campusconnect.recruiter.applications;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Application;
import com.campusconnect.common.domain.ApplicationStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.file.FileStorageService;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recruiter résumé-snapshot pre-signed URL (Story 6.1, AC6): 15-min URL on demand, owner + drive scoped 404s. */
@SpringBootTest
@Testcontainers
class ApplicantResumeUrlTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    /** A deterministic stub so the test asserts URL + TTL without a live MinIO; echoes the key and TTL. */
    @TestConfiguration
    static class StubStorageConfig {
        @Bean @Primary FileStorageService stubFileStorage() {
            return new FileStorageService() {
                @Override public void put(String key, byte[] bytes, String contentType) { }
                @Override public String presignedGetUrl(String key, Duration ttl) {
                    return "https://signed.example/" + key + "?ttl=" + ttl.toSeconds();
                }
            };
        }
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
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
        tenantId = seedTenant("vignan");
        recruiterId = seedRecruiter("hr@acme.com");
        driveId = seedDrive(tenantId, recruiterId);
    }

    @Test
    void resume_ownApplicant_returns200_withUrlAnd15MinTtl() throws Exception {
        String appId = seedApplication(driveId, "alice", "resumes/" + tenantId + "/alice/snap.pdf");
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants/{a}/resume", driveId, appId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://signed.example/resumes/" + tenantId + "/alice/snap.pdf?ttl=900"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900));
    }

    @Test
    void resume_missingSnapshotKey_is404() throws Exception {
        String appId = seedApplication(driveId, "alice", null);
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants/{a}/resume", driveId, appId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resume_applicationOfAnotherDrive_is404() throws Exception {
        String otherDrive = seedDrive(tenantId, recruiterId); // also mine, but a different drive
        String appId = seedApplication(otherDrive, "alice", "resumes/x/snap.pdf");
        // ask via THIS drive's path for an application that belongs to the other drive → mismatch → 404
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants/{a}/resume", driveId, appId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resume_otherRecruitersDrive_is404() throws Exception {
        String otherRecruiter = seedRecruiter("hr2@beta.com");
        String otherDrive = seedDrive(tenantId, otherRecruiter);
        String appId = seedApplication(otherDrive, "bob", "resumes/y/snap.pdf");
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants/{a}/resume", otherDrive, appId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resume_crossTenant_is404() throws Exception {
        String otherTenant = seedTenant("other");
        String foreignDrive = seedDrive(otherTenant, "ghost");
        String appId = seedApplicationIn(otherTenant, foreignDrive, "ghoststudent", "resumes/z/snap.pdf");
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants/{a}/resume", foreignDrive, appId)
                        .header(HttpHeaders.AUTHORIZATION, token(recruiterId, Role.RECRUITER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resume_noToken_is401() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives/{d}/applicants/{a}/resume", driveId, "any"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    private String seedApplication(String drive, String studentId, String resumeKey) {
        return seedApplicationIn(tenantId, drive, studentId, resumeKey);
    }

    private String seedApplicationIn(String tenant, String drive, String studentId, String resumeKey) {
        Application a = new Application();
        a.setTenantId(tenant);
        a.setStudentId(studentId);
        a.setDriveId(drive);
        a.setStatus(ApplicationStatus.APPLIED);
        a.setAppliedAt(Instant.parse("2026-06-01T00:00:00Z"));
        a.setResumeSnapshotKey(resumeKey);
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
}
