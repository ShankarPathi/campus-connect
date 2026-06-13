package com.campusconnect.recruiter.drives;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.DriveStatus;
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

import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ownership isolation for drives (Story 4.1) — the two axes of the 2.5 "recruiter → own drives"
 * convention: a recruiter cannot read/edit another recruiter's drive in the <b>same tenant</b>
 * (cross-owner) or in <b>another tenant</b> (cross-tenant). Both are a 404 (never reveal existence).
 */
@SpringBootTest
@Testcontainers
class DriveIsolationTest {

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

    String tenantA;
    String tenantB;
    String recruiterA1; // the acting recruiter (tenant A, owns nothing)
    String driveOfA2;   // a drive owned by another recruiter in tenant A
    String driveOfB1;   // a drive owned by a recruiter in tenant B

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Drive.class);

        tenantA = seedTenant("alpha");
        tenantB = seedTenant("beta");
        recruiterA1 = seedRecruiter(tenantA, "a1@acme.com");
        String recruiterA2 = seedRecruiter(tenantA, "a2@acme.com");
        String recruiterB1 = seedRecruiter(tenantB, "b1@acme.com");
        driveOfA2 = seedDrive(tenantA, recruiterA2);
        driveOfB1 = seedDrive(tenantB, recruiterB1);
    }

    @Test
    void recruiter_cannotReadAnotherRecruitersDriveInSameTenant() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives/{id}", driveOfA2).header(HttpHeaders.AUTHORIZATION, tokenA1()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recruiter_cannotEditAnotherRecruitersDriveInSameTenant() throws Exception {
        mockMvc.perform(put("/api/recruiter/drives/{id}", driveOfA2).header(HttpHeaders.AUTHORIZATION, tokenA1())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"hijack\"}"))
                .andExpect(status().isNotFound());
        // the victim's drive is untouched
        assertRole(driveOfA2, "Owned Role");
    }

    @Test
    void recruiter_cannotReadAnotherTenantsDrive() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives/{id}", driveOfB1).header(HttpHeaders.AUTHORIZATION, tokenA1()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recruiterList_excludesOtherOwnersDrives() throws Exception {
        mockMvc.perform(get("/api/recruiter/drives").header(HttpHeaders.AUTHORIZATION, tokenA1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0)); // A1 owns none; A2's drive is invisible
    }

    // ── helpers ──

    private String tokenA1() {
        return "Bearer " + jwtService.issueAccessToken(recruiterA1, Role.RECRUITER, tenantA);
    }

    private void assertRole(String driveId, String expected) {
        Drive d = mongoTemplate.findById(driveId, Drive.class);
        org.assertj.core.api.Assertions.assertThat(d).isNotNull();
        org.assertj.core.api.Assertions.assertThat(d.getRole()).isEqualTo(expected);
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(List.of("CSE"));
        t.setBatches(List.of("2026"));
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedRecruiter(String tid, String email) {
        User u = new User();
        u.setTenantId(tid);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(Role.RECRUITER);
        u.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(u).getId();
    }

    private String seedDrive(String tid, String ownerId) {
        Drive d = new Drive();
        d.setTenantId(tid);
        d.setCreatedBy(ownerId);
        d.setCompanyName("Acme");
        d.setRole("Owned Role");
        d.setStatus(DriveStatus.DRAFT);
        return mongoTemplate.save(d).getId();
    }
}
