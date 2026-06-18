package com.campusconnect.recruiter.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RefreshToken;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recruiter login (Story 2.3) — the PENDING_APPROVAL / REJECTED gates that 2.2 set up, plus rotation. */
@SpringBootTest
@Testcontainers
class RecruiterLoginTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
        // Story 2.5: raise the rate limits far above any test so the shared RateLimiter never trips here.
        registry.add("app.ratelimit.login.limit", () -> "100000");
        registry.add("app.ratelimit.otp.limit", () -> "100000");
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), RefreshToken.class);
    }

    @Test
    void login_activeRecruiter_returnsTokenAndCookie() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "hr@acme.com", AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/recruiter/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "hr@acme.com", "s3cret-pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("RECRUITER"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(1800))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    void login_pendingApproval_isRecruiterNotApproved403() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "hr@acme.com", AccountStatus.PENDING_APPROVAL);

        mockMvc.perform(post("/api/recruiter/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "hr@acme.com", "s3cret-pw")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RECRUITER_NOT_APPROVED"));
    }

    @Test
    void login_rejected_isAccountInactive403() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "hr@acme.com", AccountStatus.REJECTED);

        mockMvc.perform(post("/api/recruiter/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "hr@acme.com", "s3cret-pw")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void refresh_rotates_andLogoutInvalidates() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "hr@acme.com", AccountStatus.ACTIVE);
        Cookie session = login();

        mockMvc.perform(post("/api/recruiter/auth/refresh").cookie(session))
                .andExpect(status().isOk());

        // the original cookie was rotated → replay fails
        mockMvc.perform(post("/api/recruiter/auth/refresh").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    private Cookie login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/recruiter/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "hr@acme.com", "s3cret-pw")))
                .andExpect(status().isOk())
                .andReturn();
        return r.getResponse().getCookie("refreshToken");
    }

    private String loginJson(String college, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("collegeCode", college);
            put("email", email);
            put("password", password);
        }});
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private void seedUser(String tenantId, String email, AccountStatus status) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode("s3cret-pw"));
        u.setRole(Role.RECRUITER);
        u.setAccountStatus(status);
        userRepository.save(u);
    }
}
