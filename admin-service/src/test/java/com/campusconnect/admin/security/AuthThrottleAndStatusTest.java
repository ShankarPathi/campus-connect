package com.campusconnect.admin.security;

import com.campusconnect.common.domain.AccountStatus;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 2.5 at the HTTP layer: login throttle (5/IP/15min), OTP throttle (3/email/hr), and the
 * per-request active-status gate. This class pins the real default limits (login 5, OTP 3) in its own
 * context so it can prove throttling; each method uses a distinct IP / email to stay independent.
 */
@SpringBootTest
@Testcontainers
class AuthThrottleAndStatusTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
        registry.add("app.ratelimit.login.limit", () -> "5");
        registry.add("app.ratelimit.otp.limit", () -> "3");
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    JwtService jwtService;
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
    String tenantId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        tenantId = seedTenant("vignan");
    }

    // ── login throttle (AC 1) ──

    @Test
    void sixthLoginFromSameIp_is429RateLimited() throws Exception {
        String ip = "203.0.113.7"; // unique IP for this method so the shared limiter does not bleed across tests
        // 5 attempts are admitted (here failing auth → 401), then the 6th is throttled
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/admin/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("vignan", "nobody@vignan.edu", "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/admin/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "nobody@vignan.edu", "wrong-password")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    // ── OTP throttle (AC 2) — the security-critical fix for Story 2.4's brute-force gap ──

    @Test
    void fourthForgotForSameEmail_is429RateLimited() throws Exception {
        String email = "throttle@otp.test"; // unique email; keyed independent of any real account
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/admin/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(forgotJson("vignan", email)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/admin/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotJson("vignan", email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    // ── per-request active-status gate (AC 5, 6) ──

    @Test
    void deactivatedAdminToken_is403AccountInactive_onAuthenticatedEndpoint() throws Exception {
        String adminId = seedUser("dead-admin", "dead@vignan.edu", Role.COLLEGE_ADMIN, AccountStatus.DEACTIVATED);

        mockMvc.perform(get("/api/admin/recruiters").param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.issueAccessToken(adminId, Role.COLLEGE_ADMIN, tenantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void activeAdminToken_reachesEndpoint_200() throws Exception {
        String adminId = seedUser("live-admin", "live@vignan.edu", Role.COLLEGE_ADMIN, AccountStatus.ACTIVE);

        mockMvc.perform(get("/api/admin/recruiters").param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.issueAccessToken(adminId, Role.COLLEGE_ADMIN, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── helpers ──

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedUser(String id, String email, Role role, AccountStatus status) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode("whatever-pw"));
        u.setRole(role);
        u.setAccountStatus(status);
        return userRepository.save(u).getId();
    }

    private String loginJson(String college, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("collegeCode", college);
            put("email", email);
            put("password", password);
        }});
    }

    private String forgotJson(String college, String email) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("collegeCode", college);
            put("email", email);
        }});
    }
}
